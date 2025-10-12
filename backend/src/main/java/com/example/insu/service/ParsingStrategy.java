package com.example.insu.service;

import java.io.File;
import java.util.Map;

/**
 * PDF 파싱 전략 인터페이스
 * 다양한 파싱 방법을 통합하기 위한 공통 인터페이스
 */
public interface ParsingStrategy {
    
    /**
     * PDF 파일을 파싱하여 보험 조건 추출
     * 
     * @param pdfFile PDF 파일
     * @param insuCd 보험코드
     * @return 추출된 조건 맵 (insuTerm, payTerm, ageRange, renew, specialNotes)
     */
    Map<String, String> parse(File pdfFile, String insuCd);
    
    /**
     * 파싱 전략의 이름 반환
     * 
     * @return 전략 이름 (예: "LLM", "Regex", "BusinessMethod")
     */
    String getStrategyName();
    
    /**
     * 파싱 전략의 우선순위 (낮을수록 먼저 시도)
     * 
     * @return 우선순위 (1-10)
     */
    int getPriority();
    
    /**
     * 파싱 전략이 사용 가능한지 확인
     * 
     * @return 사용 가능 여부
     */
    boolean isAvailable();
    
    /**
     * 파싱 결과의 신뢰도 평가
     * 
     * @param result 파싱 결과
     * @return 신뢰도 (0-100)
     */
    int evaluateConfidence(Map<String, String> result);
}


