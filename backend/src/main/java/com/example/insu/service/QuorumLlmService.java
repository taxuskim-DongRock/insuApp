package com.example.insu.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 쿼럼 기반 LLM 서비스
 * 문제 해결:
 * - All-or-Nothing → 2/3 일치 시 조기 종료
 * - 고정 30초 타임아웃 → 모델별 동적 타임아웃
 * - 부분 성공 미처리 → 2개 성공 시 OK
 */
@Slf4j
@Service
public class QuorumLlmService {
    
    private final OllamaService ollamaService;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    
    // 모델별 동적 타임아웃 (p95 기반)
    private final Map<String, Long> modelTimeouts = new ConcurrentHashMap<>();
    
    public QuorumLlmService(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
        
        // 초기 타임아웃 설정
        modelTimeouts.put("Llama", 10000L);      // 10초
        modelTimeouts.put("Mistral", 8000L);     // 8초
        modelTimeouts.put("CodeLlama", 9000L);   // 9초
    }
    
    /**
     * 쿼럼 기반 파싱: 2/3 일치 시 조기 종료
     * 
     * @param prompt LLM 프롬프트
     * @param insuCd 보험코드
     * @return 통합된 파싱 결과
     */
    public Map<String, String> parseWithQuorum(String prompt, String insuCd) {
        log.info("=== 쿼럼 기반 LLM 파싱 시작: {} ===", insuCd);
        
        long overallStart = System.currentTimeMillis();
        
        // 3개 모델 병렬 실행 (각자의 타임아웃)
        List<CompletableFuture<ModelResult>> futures = Arrays.asList(
            CompletableFuture.supplyAsync(() -> 
                callModel("Llama", () -> ollamaService.parseWithLlama(prompt, insuCd)), executor),
            CompletableFuture.supplyAsync(() -> 
                callModel("Mistral", () -> ollamaService.parseWithMistral(prompt, insuCd)), executor),
            CompletableFuture.supplyAsync(() -> 
                callModel("CodeLlama", () -> ollamaService.parseWithCodeLlama(prompt, insuCd)), executor)
        );
        
        // 결과 수집 (쿼럼 달성 시 조기 종료)
        List<ModelResult> results = new ArrayList<>();
        long maxWaitTime = 30000;  // 전체 최대 30초
        long deadline = overallStart + maxWaitTime;
        
        for (CompletableFuture<ModelResult> future : futures) {
            try {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    log.warn("전체 타임아웃 도달");
                    break;
                }
                
                ModelResult result = future.get(remaining, TimeUnit.MILLISECONDS);
                results.add(result);
                
                log.info("[{}] 완료 - 성공: {}, 소요: {}ms", 
                        result.getModelName(), result.isSuccess(), result.getElapsedTime());
                
                // 쿼럼 확인: 2개 이상 일치 시 조기 종료
                if (results.size() >= 2 && hasQuorum(results)) {
                    long elapsed = System.currentTimeMillis() - overallStart;
                    log.info("✓ 쿼럼 달성 (2/3 합의), 조기 종료! 총 소요: {}ms", elapsed);
                    
                    // 나머지 요청 취소
                    futures.forEach(f -> f.cancel(true));
                    break;
                }
                
            } catch (TimeoutException e) {
                log.warn("모델 대기 타임아웃");
            } catch (Exception e) {
                log.error("모델 실행 오류: {}", e.getMessage());
            }
        }
        
        long totalElapsed = System.currentTimeMillis() - overallStart;
        log.info("=== 쿼럼 파싱 완료: {}ms (성공: {}/3) ===", totalElapsed, 
                results.stream().filter(ModelResult::isSuccess).count());
        
        // 결과 통합
        Map<String, String> integrated = integrateResultsWithQuorum(results);
        
        // 타임아웃 동적 조정 (p95 학습)
        updateDynamicTimeouts(results);
        
        return integrated;
    }
    
    /**
     * 모델 호출 (개별 타임아웃 적용)
     */
    private ModelResult callModel(String modelName, 
                                  Supplier<CompletableFuture<Map<String, String>>> supplier) {
        long start = System.currentTimeMillis();
        long timeout = modelTimeouts.getOrDefault(modelName, 10000L);
        
        try {
            log.debug("[{}] 호출 시작 (타임아웃: {}ms)", modelName, timeout);
            
            Map<String, String> result = supplier.get()
                .get(timeout, TimeUnit.MILLISECONDS);
            
            long elapsed = System.currentTimeMillis() - start;
            
            return new ModelResult(modelName, result, true, elapsed);
            
        } catch (TimeoutException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[{}] 타임아웃: {}ms", modelName, elapsed);
            return new ModelResult(modelName, null, false, elapsed);
            
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[{}] 실패: {} ({}ms)", modelName, e.getMessage(), elapsed);
            return new ModelResult(modelName, null, false, elapsed);
        }
    }
    
    /**
     * 쿼럼 확인: 2개 이상의 모델이 핵심 필드에서 일치하는지
     */
    private boolean hasQuorum(List<ModelResult> results) {
        if (results.size() < 2) {
            return false;
        }
        
        // 성공한 모델만 필터링
        List<Map<String, String>> successResults = results.stream()
            .filter(ModelResult::isSuccess)
            .map(ModelResult::getResult)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (successResults.size() < 2) {
            return false;
        }
        
        // 핵심 필드 (insuTerm, payTerm) 일치도 확인
        Map<String, Long> insuTermVotes = successResults.stream()
            .map(r -> r.get("insuTerm"))
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        
        Map<String, Long> payTermVotes = successResults.stream()
            .map(r -> r.get("payTerm"))
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        
        // 2개 이상이 동일한 값이면 쿼럼
        boolean insuTermQuorum = insuTermVotes.values().stream().anyMatch(count -> count >= 2);
        boolean payTermQuorum = payTermVotes.values().stream().anyMatch(count -> count >= 2);
        
        return insuTermQuorum && payTermQuorum;
    }
    
    /**
     * 쿼럼 기반 결과 통합 (투표)
     */
    private Map<String, String> integrateResultsWithQuorum(List<ModelResult> results) {
        Map<String, String> integrated = new LinkedHashMap<>();
        
        // 성공한 모델만 사용
        List<Map<String, String>> successResults = results.stream()
            .filter(ModelResult::isSuccess)
            .map(ModelResult::getResult)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (successResults.isEmpty()) {
            log.warn("모든 LLM 모델 실패");
            return getDefaultResult();
        }
        
        // 각 필드마다 투표
        for (String field : Arrays.asList("insuTerm", "payTerm", "ageRange", "renew")) {
            String winner = voteForField(field, successResults);
            integrated.put(field, winner);
        }
        
        // 메타 정보
        integrated.put("specialNotes", 
            String.format("쿼럼 기반 통합 (%d/3 모델 성공)", successResults.size()));
        
        return integrated;
    }
    
    /**
     * 필드별 투표 (다수결)
     */
    private String voteForField(String field, List<Map<String, String>> results) {
        Map<String, Long> votes = results.stream()
            .map(r -> r.get(field))
            .filter(Objects::nonNull)
            .filter(v -> !v.equals("—"))
            .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        
        if (votes.isEmpty()) {
            return "—";
        }
        
        // 최다 득표 값 선택
        return votes.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("—");
    }
    
    /**
     * 동적 타임아웃 업데이트 (p95 기반)
     */
    private void updateDynamicTimeouts(List<ModelResult> results) {
        for (ModelResult result : results) {
            if (result.isSuccess()) {
                String modelName = result.getModelName();
                long elapsed = result.getElapsedTime();
                
                // 현재 타임아웃의 120%로 조정 (여유 있게)
                long newTimeout = (long) (elapsed * 1.2);
                
                // 최소 5초, 최대 20초
                newTimeout = Math.max(5000, Math.min(20000, newTimeout));
                
                modelTimeouts.put(modelName, newTimeout);
                
                log.debug("[{}] 타임아웃 조정: {}ms → {}ms", 
                        modelName, elapsed, newTimeout);
            }
        }
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
        result.put("specialNotes", "쿼럼 파싱 실패");
        return result;
    }
    
    /**
     * 모델 결과 DTO
     */
    @Data
    public static class ModelResult {
        private final String modelName;
        private final Map<String, String> result;
        private final boolean success;
        private final long elapsedTime;
    }
    
    /**
     * 타임아웃 통계 조회
     */
    public Map<String, Long> getTimeoutStatistics() {
        return new HashMap<>(modelTimeouts);
    }
}


