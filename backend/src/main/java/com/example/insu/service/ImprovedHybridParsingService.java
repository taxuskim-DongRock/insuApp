package com.example.insu.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.Base64;

/**
 * 개선된 하이브리드 파싱 서비스 (Caffeine Cache 적용)
 * 문제 해결:
 * - 무제한 성장 → 최대 1000개 제한
 * - TTL 부재 → 24시간 자동 만료
 * - 버전 관리 부재 → PDF 해시 + 파서 버전
 */
@Slf4j
@Service
public class ImprovedHybridParsingService {
    
    private final List<ParsingStrategy> strategies;
    private static final String PARSER_VERSION = "1.0.0";  // 배포 시 변경
    
    public ImprovedHybridParsingService(List<ParsingStrategy> strategies) {
        this.strategies = strategies;
        
        // 우선순위 순으로 정렬
        this.strategies.sort(Comparator.comparingInt(ParsingStrategy::getPriority));
        
        log.info("개선된 하이브리드 파싱 서비스 초기화 - {} 개 전략 로드", strategies.size());
        strategies.forEach(s -> log.info("  - {} (우선순위: {})", s.getStrategyName(), s.getPriority()));
        
        // UW_CODE_MAPPING 기반 파싱 전략이 있는지 확인
        boolean hasUwMappingStrategy = strategies.stream()
            .anyMatch(s -> s.getStrategyName().contains("UW_CODE_MAPPING"));
        
        if (hasUwMappingStrategy) {
            log.info("✅ UW_CODE_MAPPING 기반 검증 파싱 전략이 활성화되었습니다");
        } else {
            log.warn("⚠️ UW_CODE_MAPPING 기반 검증 파싱 전략이 없습니다. 정확도가 떨어질 수 있습니다.");
        }
    }
    
    /**
     * Caffeine Cache 적용 파싱
     * 
     * @Cacheable: Spring Cache 추상화 사용
     * - value: 캐시 이름 (CacheConfig에서 정의)
     * - key: PDF 해시 + 보험코드 + 파서 버전
     * - 자동으로 캐시 관리 (크기, TTL, 통계)
     */
    @Cacheable(value = "parsingCache", key = "#root.target.generateCacheKey(#pdfFile, #insuCd)")
    public Map<String, String> parseWithMultipleStrategies(File pdfFile, String insuCd) {
        log.info("=== 개선된 하이브리드 파싱 시작: {} ===", insuCd);
        
        // 각 전략 시도
        List<ParseResult> results = new ArrayList<>();
        
        for (ParsingStrategy strategy : strategies) {
            if (!strategy.isAvailable()) {
                log.debug("전략 사용 불가: {}", strategy.getStrategyName());
                continue;
            }
            
            try {
                log.info("[전략 {}] 파싱 시작...", strategy.getStrategyName());
                long startTime = System.currentTimeMillis();
                
                Map<String, String> result = strategy.parse(pdfFile, insuCd);
                int confidence = strategy.evaluateConfidence(result);
                
                long elapsed = System.currentTimeMillis() - startTime;
                
                ParseResult parseResult = new ParseResult(
                    strategy.getStrategyName(),
                    result,
                    confidence,
                    elapsed
                );
                
                results.add(parseResult);
                
                log.info("[전략 {}] 파싱 완료 - 신뢰도: {}%, 소요시간: {}ms", 
                        strategy.getStrategyName(), confidence, elapsed);
                
                // 신뢰도 85% 이상이면 즉시 반환
                if (confidence >= 85) {
                    log.info("높은 신뢰도 달성 ({}%), 추가 전략 생략", confidence);
                    printSummary(results, parseResult);
                    return result;
                }
                
            } catch (Exception e) {
                log.error("[전략 {}] 파싱 실패: {}", strategy.getStrategyName(), e.getMessage());
            }
        }
        
        // 최적 결과 선택
        Map<String, String> bestResult = selectBestResult(results);
        
        printSummary(results, findBestParseResult(results));
        
        log.info("=== 개선된 하이브리드 파싱 완료: {} ===", insuCd);
        return bestResult;
    }
    
    /**
     * 개선된 캐시 키 생성
     * - PDF 해시 (내용 변경 감지)
     * - 보험코드
     * - 파서 버전 (배포 시 무효화)
     */
    public String generateCacheKey(File pdfFile, String insuCd) {
        String pdfHash = calculateFileHash(pdfFile);
        return String.format("%s_%s_%s", pdfHash, insuCd, PARSER_VERSION);
    }
    
    /**
     * PDF 파일 해시 계산 (SHA-256)
     */
    private String calculateFileHash(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }
            byte[] hash = md.digest();
            // Base64 인코딩 (파일명으로 사용하기 위해)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 16);
        } catch (Exception e) {
            log.warn("파일 해시 계산 실패, 파일명 사용: {}", file.getName());
            return file.getName();
        }
    }
    
    /**
     * 최적 결과 선택
     */
    private Map<String, String> selectBestResult(List<ParseResult> results) {
        if (results.isEmpty()) {
            log.warn("모든 파싱 전략 실패");
            return getDefaultResult();
        }
        
        // 신뢰도 기준 정렬
        results.sort(Comparator.comparingInt(ParseResult::getConfidence).reversed());
        
        ParseResult best = results.get(0);
        log.info("최적 결과 선택: {} (신뢰도: {}%)", best.getStrategyName(), best.getConfidence());
        
        return best.getResult();
    }
    
    /**
     * 최적 ParseResult 찾기
     */
    private ParseResult findBestParseResult(List<ParseResult> results) {
        return results.stream()
            .max(Comparator.comparingInt(ParseResult::getConfidence))
            .orElse(null);
    }
    
    /**
     * 파싱 요약 출력
     */
    private void printSummary(List<ParseResult> allResults, ParseResult bestResult) {
        log.info("--- 파싱 결과 요약 ---");
        allResults.forEach(r -> 
            log.info("  {} - 신뢰도: {}%, 시간: {}ms", 
                    r.getStrategyName(), r.getConfidence(), r.getElapsedTime())
        );
        
        if (bestResult != null) {
            log.info("최종 선택: {} (신뢰도: {}%)", 
                    bestResult.getStrategyName(), bestResult.getConfidence());
        }
        log.info("---------------------");
    }
    
    /**
     * 기본 결과
     */
    private Map<String, String> getDefaultResult() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("insuTerm", "—");
        result.put("payTerm", "—");
        result.put("ageRange", "—");
        result.put("renew", "—");
        result.put("specialNotes", "모든 파싱 전략 실패");
        return result;
    }
    
    /**
     * 파싱 결과 클래스
     */
    private static class ParseResult {
        private final String strategyName;
        private final Map<String, String> result;
        private final int confidence;
        private final long elapsedTime;
        
        public ParseResult(String strategyName, Map<String, String> result, 
                          int confidence, long elapsedTime) {
            this.strategyName = strategyName;
            this.result = result;
            this.confidence = confidence;
            this.elapsedTime = elapsedTime;
        }
        
        public String getStrategyName() { return strategyName; }
        public Map<String, String> getResult() { return result; }
        public int getConfidence() { return confidence; }
        public long getElapsedTime() { return elapsedTime; }
    }
}

