package com.example.insu.service;

import com.example.insu.dto.UwCodeMappingData;
import com.example.insu.dto.ValidationResult;
import com.example.insu.dto.LearnedPattern;
import com.example.insu.mapper.LearnedPatternMapper;
import com.example.insu.util.PdfParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * UW_CODE_MAPPING 기반 검증 파싱 전략
 */
@Slf4j
@Service
public class UwMappingValidatedParsingStrategy implements ParsingStrategy {
    
    @Autowired
    private UwCodeMappingValidationService uwMappingValidationService;
    
    @Autowired
    private OllamaService ollamaService;
    
    @Autowired
    private LearnedPatternMapper learnedPatternMapper;
    
    @Autowired(required = false)
    private LearnedPatternScoringService patternScoringService;
    
    @Override
    public Map<String, String> parse(File pdfFile, String insuCd) {
        try {
            log.info("=== UW_CODE_MAPPING 검증 파싱 시작: {} ===", insuCd);
            
            // 1. LLM 파싱 실행
            String prompt = buildPrompt(pdfFile, insuCd);
            Map<String, String> llmResult = ollamaService.parseWithLlama(prompt, insuCd).get();
            
            log.debug("LLM 파싱 결과: {}", llmResult);
            
            // 2. 학습된 패턴 적용
            llmResult = applyLearnedPatterns(insuCd, llmResult);
            log.info("학습된 패턴 적용 후: {}", llmResult);
            
            // 3. 가입나이 패턴 보완 정책 적용
            llmResult = applyAgePatternCorrection(insuCd, llmResult);
            log.info("가입나이 패턴 보완 후: {}", llmResult);
            
            // 3. UW_CODE_MAPPING 검증
            ValidationResult validation = uwMappingValidationService.validateWithUwMapping(insuCd, llmResult);
            
            if ("VALID".equals(validation.getStatus()) && validation.getConfidence() >= 80) {
                log.info("UW_CODE_MAPPING 검증 통과: {} (신뢰도: {}%)", insuCd, validation.getConfidence());
                llmResult.put("validationSource", "UW_CODE_MAPPING");
                llmResult.put("validationConfidence", String.valueOf(validation.getConfidence()));
                return llmResult;
            } else {
                log.warn("UW_CODE_MAPPING 검증 실패: {} (신뢰도: {}%)", insuCd, validation.getConfidence());
                log.warn("불일치 항목: {}", validation.getMismatchedTerms());
                
                // 검증 실패 시 UW_CODE_MAPPING 데이터로 보정
                return correctWithUwMapping(insuCd, llmResult, validation.getMappingData());
            }
            
        } catch (Exception e) {
            log.error("UW_CODE_MAPPING 검증 파싱 오류: {}", e.getMessage(), e);
            
            // 오류 시 UW_CODE_MAPPING 데이터 직접 사용
            return getUwMappingDataDirectly(insuCd);
        }
    }
    
    /**
     * 프롬프트 생성
     */
    private String buildPrompt(File pdfFile, String insuCd) {
    try {
      String text = PdfParser.readAllText(pdfFile);
      return String.format("""
                다음 보험 상품 문서에서 보험기간, 납입기간, 가입나이, 갱신여부 정보를 JSON 형식으로 추출해줘.
                상품코드: %s
                문서 내용:
                %s
                
                출력 형식:
                {
                    "insuTerm": "보험기간", 
                    "payTerm": "납입기간", 
                    "ageRange": "가입나이",
                    "renew": "갱신여부"
                }
                
                중요:
                - 보험기간: 종신, 90세만기, 100세만기 등 정확히 추출
                - 납입기간: 10년납, 15년납, 20년납, 30년납, 전기납 등 정확히 추출
                - 가입나이: 남:15~80, 여:15~80 형태로 추출
                - 갱신여부: 갱신형 또는 비갱신형
                """, insuCd, text);
        } catch (Exception e) {
            log.error("프롬프트 생성 오류: {}", e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * 학습된 패턴 적용
     */
    private Map<String, String> applyLearnedPatterns(String insuCd, Map<String, String> llmResult) {
        try {
            log.info("학습된 패턴 조회 시작: {}", insuCd);
            
            // 해당 상품코드의 모든 학습된 패턴 조회
            List<LearnedPattern> patterns = learnedPatternMapper.selectAllByInsuCd(insuCd);
            
            if (patterns.isEmpty()) {
                log.info("학습된 패턴 없음: {}", insuCd);
                return llmResult;
            }
            
            log.info("학습된 패턴 {} 개 발견: {}", patterns.size(), insuCd);
            
            // 각 패턴의 상세 정보 로그
            for (LearnedPattern pattern : patterns) {
                log.info("패턴 상세: {} {} = {} (신뢰도: {}%, 우선순위: {})", 
                        insuCd, pattern.getFieldName(), pattern.getPatternValue(), 
                        pattern.getConfidenceScore(), pattern.getPriority());
            }
            
            // 패턴을 우선순위별로 정렬 (높은 우선순위부터)
            patterns.sort((p1, p2) -> Integer.compare(p2.getPriority(), p1.getPriority()));
            
            // 각 필드에 대해 학습된 패턴 적용
            Map<String, String> result = new LinkedHashMap<>(llmResult);
            boolean patternApplied = false;
            
            for (LearnedPattern pattern : patterns) {
                String fieldName = pattern.getFieldName();
                String patternValue = pattern.getPatternValue();
                int confidence = pattern.getConfidenceScore();
                int priority = pattern.getPriority();
                
                // 품질 스코어링 적용
                int qualityScore = confidence; // 기본값
                if (patternScoringService != null) {
                    qualityScore = patternScoringService.calculatePatternScore(pattern);
                    log.debug("패턴 품질 점수: {} {} = {} 점 (등급: {})", 
                             insuCd, fieldName, qualityScore, 
                             patternScoringService.getQualityGrade(qualityScore));
                }
                
                // 품질 점수 60 이상만 적용 (기존: 신뢰도 70, 우선순위 50)
                boolean isApplicable = patternScoringService != null ? 
                                      patternScoringService.isApplicable(pattern) :
                                      (confidence >= 70 && priority >= 50);
                
                if (isApplicable) {
                    String currentValue = result.get(fieldName);
                    
                    // 현재 값이 기본값이거나 복잡한 파싱 결과인 경우 패턴 적용
                    if (currentValue == null || currentValue.trim().isEmpty() || 
                        currentValue.equals("—") || 
                        currentValue.contains("종신:") || 
                        currentValue.length() > 100) { // 복잡한 파싱 결과도 덮어쓰기
                        
                        result.put(fieldName, patternValue);
                        patternApplied = true;
                        
                        log.info("✅ 학습된 패턴 적용: {} {} = {} (품질점수: {}, 신뢰도: {}%, 우선순위: {})", 
                                insuCd, fieldName, patternValue, qualityScore, confidence, priority);
                    } else {
                        log.debug("패턴 적용 스킵: {} {} 현재값='{}' (품질점수: {})", 
                                insuCd, fieldName, currentValue, qualityScore);
                    }
                } else {
                    log.warn("❌ 패턴 품질 부족으로 적용 불가: {} {} (품질점수: {})", 
                            insuCd, fieldName, qualityScore);
                }
            }
            
            if (patternApplied) {
                result.put("validationSource", "LEARNED_PATTERN");
                log.info("학습된 패턴 적용 완료: {}", insuCd);
            } else {
                log.info("적용 가능한 학습된 패턴 없음: {}", insuCd);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("학습된 패턴 적용 중 오류: {} - {}", insuCd, e.getMessage(), e);
            return llmResult; // 오류 시 원본 반환
        }
    }
    
    /**
     * 가입나이 패턴 보완 정책 적용
     * 같은 코드 내의 다른 납입기간/보험기간의 정상적인 가입나이 패턴을 참조
     */
    private Map<String, String> applyAgePatternCorrection(String insuCd, Map<String, String> llmResult) {
        try {
            String currentAgeRange = llmResult.get("ageRange");
            
            // 가입나이가 정상적인 패턴인지 확인
            if (isNormalAgePattern(currentAgeRange)) {
                log.debug("가입나이가 정상 패턴이므로 보완 불필요: {} = {}", insuCd, currentAgeRange);
                return llmResult;
            }
            
            log.info("가입나이 패턴 보완 필요: {} = {}", insuCd, currentAgeRange);
            
            // 같은 코드의 UW_CODE_MAPPING 데이터에서 정상적인 가입나이 패턴 찾기
            String correctedAgeRange = findNormalAgePatternFromUwMapping(insuCd);
            
            if (correctedAgeRange != null && !correctedAgeRange.equals(currentAgeRange)) {
                llmResult.put("ageRange", correctedAgeRange);
                llmResult.put("validationSource", "AGE_PATTERN_CORRECTION");
                log.info("가입나이 패턴 보완 적용: {} {} -> {}", insuCd, currentAgeRange, correctedAgeRange);
            } else {
                log.info("적용 가능한 정상 가입나이 패턴 없음: {}", insuCd);
            }
            
            return llmResult;
            
        } catch (Exception e) {
            log.error("가입나이 패턴 보완 중 오류: {} - {}", insuCd, e.getMessage(), e);
            return llmResult; // 오류 시 원본 반환
        }
    }
    
    /**
     * 정상적인 가입나이 패턴인지 확인
     * 예: "남: 30 ~ 70, 여: 30 ~ 70"
     */
    private boolean isNormalAgePattern(String ageRange) {
        if (ageRange == null || ageRange.trim().isEmpty() || ageRange.equals("—")) {
            return false;
        }
        
        // 정상 패턴: "남: XX ~ XX, 여: XX ~ XX" 형태
        String normalPattern = "남:\\s*\\d+\\s*~\\s*\\d+\\s*,\\s*여:\\s*\\d+\\s*~\\s*\\d+";
        
        // 복잡한 패턴이거나 길이가 100자 이상이면 비정상으로 간주
        if (ageRange.length() > 100 || ageRange.contains("종신:")) {
            return false;
        }
        
        return ageRange.matches(normalPattern);
    }
    
    /**
     * UW_CODE_MAPPING에서 같은 코드의 정상적인 가입나이 패턴 찾기
     */
    private String findNormalAgePatternFromUwMapping(String insuCd) {
        try {
            List<UwCodeMappingData> mappingData = uwMappingValidationService.getValidationDataByCode(insuCd);
            
            if (mappingData == null || mappingData.isEmpty()) {
                log.warn("UW_CODE_MAPPING 데이터 없음: {}", insuCd);
                return null;
            }
            
            // 가장 많이 나타나는 가입나이 패턴 찾기
            Map<String, Integer> patternCount = new HashMap<>();
            
            for (UwCodeMappingData data : mappingData) {
                String maleAge = data.getEntryAgeM();
                String femaleAge = data.getEntryAgeF();
                
                if (maleAge != null && femaleAge != null && 
                    !maleAge.trim().isEmpty() && !femaleAge.trim().isEmpty()) {
                    
                    // 정규화된 패턴 생성
                    String normalizedPattern = String.format("남: %s, 여: %s", 
                        maleAge.trim(), femaleAge.trim());
                    
                    patternCount.merge(normalizedPattern, 1, Integer::sum);
                }
            }
            
            if (patternCount.isEmpty()) {
                log.warn("정상적인 가입나이 패턴 없음: {}", insuCd);
                return null;
            }
            
            // 가장 많이 나타나는 패턴 선택
            String mostCommonPattern = patternCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
            
            log.info("가장 일반적인 가입나이 패턴: {} = {} (빈도: {})", 
                    insuCd, mostCommonPattern, patternCount.get(mostCommonPattern));
            
            return mostCommonPattern;
            
        } catch (Exception e) {
            log.error("UW_CODE_MAPPING에서 가입나이 패턴 찾기 실패: {} - {}", insuCd, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * UW_CODE_MAPPING 데이터로 파싱 결과 보정
     */
    private Map<String, String> correctWithUwMapping(String insuCd, Map<String, String> llmResult, 
                                                    List<UwCodeMappingData> mappingData) {
        if (mappingData == null || mappingData.isEmpty()) {
            log.warn("매핑 데이터 없음, 빈 결과 반환: {}", insuCd);
            return getEmptyResult();
        }
        
        log.info("UW_CODE_MAPPING 데이터로 보정 실행: {}", insuCd);
        
        // UW_CODE_MAPPING에서 해당 보험코드의 모든 조합 가져오기
        Set<String> validInsuTerms = mappingData.stream()
            .map(UwCodeMappingData::getPeriodLabel)
            .collect(Collectors.toSet());
        
        Set<String> validPayTerms = mappingData.stream()
            .map(UwCodeMappingData::getPayTerm)
            .collect(Collectors.toSet());
        
        // 보정된 결과 생성
        Map<String, String> correctedResult = new LinkedHashMap<>();
        correctedResult.put("insuTerm", String.join(", ", validInsuTerms));
        correctedResult.put("payTerm", String.join(", ", validPayTerms));
        correctedResult.put("ageRange", buildAgeRangeString(mappingData));
        correctedResult.put("renew", determineRenewType(insuCd));
        correctedResult.put("specialNotes", "UW_CODE_MAPPING 기반 보정 데이터");
        correctedResult.put("validationSource", "UW_CODE_MAPPING_CORRECTED");
        correctedResult.put("originalLlmResult", llmResult.toString());
        
        log.info("UW_CODE_MAPPING 기반 보정 완료: {}", insuCd);
        return correctedResult;
    }
    
    /**
     * 가입나이 문자열 생성
     */
    private String buildAgeRangeString(List<UwCodeMappingData> mappingData) {
        StringBuilder ageRangeBuilder = new StringBuilder();
        
        // 보험기간별로 그룹화
        Map<String, List<UwCodeMappingData>> groupedByPeriod = mappingData.stream()
            .collect(Collectors.groupingBy(UwCodeMappingData::getPeriodLabel));
        
        for (Map.Entry<String, List<UwCodeMappingData>> entry : groupedByPeriod.entrySet()) {
            String period = entry.getKey();
            List<UwCodeMappingData> periodData = entry.getValue();
            
            if (ageRangeBuilder.length() > 0) {
                ageRangeBuilder.append("; ");
            }
            
            ageRangeBuilder.append(period).append(": ");
            
            // 납입기간별 가입나이 추가
            for (int i = 0; i < periodData.size(); i++) {
                UwCodeMappingData data = periodData.get(i);
                
                if (i > 0) {
                    ageRangeBuilder.append(", ");
                }
                
                ageRangeBuilder.append(String.format("%s(남:%s,여:%s)",
                    data.getPayTerm(),
                    data.getEntryAgeM(),
                    data.getEntryAgeF()));
            }
        }
        
        return ageRangeBuilder.toString();
    }
    
    /**
     * 갱신여부 판단
     */
    private String determineRenewType(String insuCd) {
        // 특약별 갱신여부 판단 로직
        if (insuCd.startsWith("8")) {
            return "갱신형"; // 8로 시작하는 특약들은 대부분 갱신형
        }
        return "비갱신형";
    }
    
    /**
     * UW_CODE_MAPPING 데이터 직접 조회
     */
    private Map<String, String> getUwMappingDataDirectly(String insuCd) {
        List<UwCodeMappingData> mappingData = uwMappingValidationService.getValidationDataByCode(insuCd);
        
        if (mappingData.isEmpty()) {
            log.warn("UW_CODE_MAPPING에 데이터 없음: {}", insuCd);
            return getEmptyResult();
        }
        
        log.info("UW_CODE_MAPPING 데이터 직접 사용: {}", insuCd);
        return correctWithUwMapping(insuCd, new HashMap<>(), mappingData);
    }
    
    /**
     * 빈 결과 반환
     */
    private Map<String, String> getEmptyResult() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("insuTerm", "—");
        result.put("payTerm", "—");
        result.put("ageRange", "—");
        result.put("renew", "—");
        result.put("specialNotes", "UW_CODE_MAPPING 데이터 없음");
        result.put("validationSource", "EMPTY");
        return result;
    }
    
    @Override
    public String getStrategyName() {
        return "UW_CODE_MAPPING 검증";
    }
    
    @Override
    public int getPriority() {
        return 1; // 최고 우선순위 (학습된 패턴 적용을 위해)
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // UW_CODE_MAPPING 테이블 접근 가능 여부 확인
            uwMappingValidationService.getValidationDataByCode("TEST");
            return true;
        } catch (Exception e) {
            log.warn("UW_CODE_MAPPING 검증 전략 사용 불가: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public int evaluateConfidence(Map<String, String> result) {
        if (result == null || result.isEmpty()) {
            return 0;
        }
        
        int score = 0;
        
        if (isValidField(result.get("insuTerm"))) score++;
        if (isValidField(result.get("payTerm"))) score++;
        if (isValidField(result.get("ageRange"))) score++;
        if (isValidField(result.get("renew"))) score++;
        
        // UW_CODE_MAPPING 기반 결과는 높은 신뢰도
        if ("UW_CODE_MAPPING".equals(result.get("validationSource")) ||
            "UW_CODE_MAPPING_CORRECTED".equals(result.get("validationSource"))) {
            return Math.max(score * 25, 90); // 최소 90% 신뢰도
        }
        
        return score * 25;
    }
    
    private boolean isValidField(String value) {
        return value != null && !value.isEmpty() && !value.equals("—");
    }
}
