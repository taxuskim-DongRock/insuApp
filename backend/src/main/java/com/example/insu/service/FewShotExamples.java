package com.example.insu.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import com.example.insu.service.UwCodeMappingFewShotService.UwCodeMappingRow;

/**
 * Phase 2: Few-Shot Learning 예시 관리
 * - 기존 하드코딩 예시 + CSV 기반 Few-Shot 통합
 */
@Slf4j
@Component
public class FewShotExamples {
    
    private final List<String> examples = new ArrayList<>();
    
    @Autowired(required = false)
    private UwCodeMappingFewShotService uwCodeMappingFewShotService;
    
    public FewShotExamples() {
        initializeExamples();
    }
    
    /**
     * 초기 Few-Shot 예시 로드
     */
    private void initializeExamples() {
        // 예시 1: 주계약 - 종신형
        examples.add("""
            [예시 1 - 주계약: 종신형]
            입력:
            상품코드: 21686
            상품명: (무)흥국생명 다(多)사랑암보험
            사업방법:
            - 보험기간: 종신
            - 납입기간: 10년납, 15년납, 20년납, 30년납
            - 가입나이: 10년납(남:만15세~80세,여:만15세~80세), 15년납(남:만15세~70세,여:만15세~70세), 20년납(남:만15세~70세,여:만15세~70세), 30년납(남:만15세~70세,여:만15세~70세)
            - 갱신여부: 비갱신형
            
            출력:
            {
              "insuTerm": "종신",
              "payTerm": "10년납, 15년납, 20년납, 30년납",
              "ageRange": "10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)",
              "renew": "비갱신형",
              "specialNotes": "주계약 - 종신형"
            }
            """);
        
        // 예시 2: 특약 - "주계약과 같음"
        examples.add("""
            [예시 2 - 특약: 주계약과 같음]
            입력:
            상품코드: 79525
            상품명: (무)다(多)사랑암진단특약
            사업방법: 보험기간, 납입기간, 가입나이는 주계약과 같음
            주계약 조건:
              - 보험기간: 종신
            - 납입기간: 10년납, 15년납, 20년납, 30년납
            - 가입나이: 10년납(남:만15세~80세,여:만15세~80세), 15년납(남:만15세~70세,여:만15세~70세), 20년납(남:만15세~70세,여:만15세~70세), 30년납(남:만15세~70세,여:만15세~70세)
            
            출력:
            {
              "insuTerm": "종신",
              "payTerm": "10년납, 15년납, 20년납, 30년납",
              "ageRange": "10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)",
              "renew": "비갱신형",
              "specialNotes": "주계약 조건 상속"
            }
            """);
        
        // 특수 조건을 가진 특약들의 예시 추가
        examples.add("""
            [예시 3 - 특수 조건 특약: 81819]
            입력:
            상품코드: 81819
            상품명: (무)원투쓰리암진단특약
            사업방법:
            - 보험기간: 90세만기, 100세만기
            - 납입기간: 10년납, 15년납, 20년납, 30년납
            - 가입나이: 90세만기와 100세만기별로 다른 조건
            - 갱신여부: 비갱신형
            
            출력:
            {
              "insuTerm": "90세만기, 100세만기",
              "payTerm": "10년납, 15년납, 20년납, 30년납",
              "ageRange": "90세만기: 10년납(남:15~75,여:15~75), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~60,여:15~60); 100세만기: 10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)",
              "renew": "비갱신형",
              "specialNotes": "원투쓰리암진단특약 - 사업방법 기준"
            }
            """);
            
        examples.add("""
            [예시 4 - 갱신형 특약: 81880]
            입력:
            상품코드: 81880
            상품명: (무)전이암진단생활비특약
            사업방법:
            - 보험기간: 5년만기, 10년만기
            - 납입기간: 전기납
            - 가입나이: 최초계약과 갱신계약 구분
            - 갱신여부: 갱신형
            
            출력:
            {
              "insuTerm": "5년만기, 10년만기",
              "payTerm": "전기납",
              "ageRange": "5년만기: 최초(남:15~80,여:15~80), 갱신(남:20~99,여:20~99); 10년만기: 최초(남:15~80,여:15~80), 갱신(남:25~99,여:25~99)",
              "renew": "갱신형",
              "specialNotes": "전이암진단생활비특약 - 사업방법 기준"
            }
            """);
    }
    
    /**
     * Few-Shot 프롬프트 생성 (CSV 기반 Few-Shot 통합)
     */
    public String buildFewShotPrompt(String pdfText, String insuCd, String productName) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("당신은 보험 문서 파싱 전문가입니다.\n");
        prompt.append("다음 예시들을 참고하여 정확히 추출하세요.\n\n");
        
        // 1. CSV 기반 Few-Shot 예시 추가 (우선순위 높음)
        if (uwCodeMappingFewShotService != null) {
            List<UwCodeMappingRow> csvExamples = getCsvFewShotExamples(insuCd);
            
            if (!csvExamples.isEmpty()) {
                prompt.append("=== 검증된 CSV Few-Shot 예시 (최우선) ===\n\n");
                
                for (int i = 0; i < Math.min(3, csvExamples.size()); i++) {
                    UwCodeMappingRow row = csvExamples.get(i);
                    prompt.append(buildCsvExample(row, i + 1)).append("\n\n");
                }
                
                log.debug("CSV Few-Shot 예시 {} 개 추가됨 (상품코드: {})", csvExamples.size(), insuCd);
            }
        }
        
        // 2. 기존 하드코딩 예시 추가 (보조)
        prompt.append("=== 추가 참고 예시 ===\n\n");
        for (String example : examples) {
            prompt.append(example).append("\n\n");
        }
        
        // 3. 실제 파싱 대상
        prompt.append("=== 이제 다음 상품을 파싱하세요 ===\n");
        prompt.append("입력:\n");
        prompt.append("상품코드: ").append(insuCd).append("\n");
        prompt.append("상품명: ").append(productName != null ? productName : "미확인").append("\n");
        prompt.append("사업방법 내용:\n").append(pdfText).append("\n\n");
        prompt.append("출력 (JSON 형식으로만 응답, 설명 없이):\n");
        
        return prompt.toString();
    }
    
    /**
     * CSV Few-Shot 예시 조회 (우선순위 로직)
     */
    private List<UwCodeMappingRow> getCsvFewShotExamples(String insuCd) {
        List<UwCodeMappingRow> examples = new ArrayList<>();
        
        try {
            // 1순위: 동일 상품코드의 예시
            List<UwCodeMappingRow> sameProduct = uwCodeMappingFewShotService.getFewShotExamples(insuCd);
            if (!sameProduct.isEmpty()) {
                examples.addAll(sameProduct.subList(0, Math.min(2, sameProduct.size())));
                log.info("동일 상품코드 CSV Few-Shot {} 개 발견: {}", examples.size(), insuCd);
                return examples;
            }
            
            // 2순위: 주계약 예시 (일반적인 패턴 학습)
            List<UwCodeMappingRow> mainContracts = uwCodeMappingFewShotService.getMainContractExamples(3);
            if (!mainContracts.isEmpty()) {
                examples.addAll(mainContracts);
                log.info("주계약 CSV Few-Shot {} 개 사용", examples.size());
                return examples;
            }
            
            // 3순위: 랜덤 예시
            examples = uwCodeMappingFewShotService.getRandomExamples(3);
            log.info("랜덤 CSV Few-Shot {} 개 사용", examples.size());
            
        } catch (Exception e) {
            log.warn("CSV Few-Shot 조회 실패: {} - {}", insuCd, e.getMessage());
        }
        
        return examples;
    }
    
    /**
     * CSV Row를 Few-Shot 예시 형식으로 변환
     */
    private String buildCsvExample(UwCodeMappingRow row, int exampleNumber) {
        return String.format("""
            [CSV 예시 %d - %s]
            입력:
            상품코드: %s
            상품명: %s
            구분: %s
            보험기간: %s
            납입기간: %s
            가입나이: 남(%s), 여(%s)
            
            출력:
            {
              "insuTerm": "%s",
              "payTerm": "%s",
              "ageRange": "남: %s, 여: %s",
              "renew": "비갱신형",
              "specialNotes": "%s - %s (출처: %s)"
            }
            """,
            exampleNumber,
            row.getProductGroup(),
            row.getCode(),
            row.getProductName(),
            row.getProductGroup(),
            row.getPeriodLabel(),
            row.getPayTerm(),
            row.getEntryAgeM(),
            row.getEntryAgeF(),
            row.getPeriodLabel(),
            row.getPayTerm(),
            row.getEntryAgeM(),
            row.getEntryAgeF(),
            row.getProductGroup(),
            row.getClassTag(),
            row.getSrcFile()
        );
    }
    
    /**
     * 새로운 예시 추가 (학습)
     */
    public void addExample(String example) {
        examples.add(example);
    }
    
    /**
     * 예시 개수 조회
     */
    public int getExampleCount() {
        return examples.size();
    }
    
    /**
     * 모든 예시 조회
     */
    public List<String> getAllExamples() {
        return new ArrayList<>(examples);
    }
}

