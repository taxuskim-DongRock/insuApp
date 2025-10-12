package com.example.insu.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 사업방법서 기반 파싱 전략 (정규식 사용)
 */
@Slf4j
@Service
public class BusinessMethodParsingStrategy implements ParsingStrategy {
    
    @Override
    public Map<String, String> parse(File pdfFile, String insuCd) {
        try {
            log.info("사업방법서 파싱 시작: {}", insuCd);
            
            // PDF 텍스트 추출
            String pdfText = extractPdfText(pdfFile);
            
            // 1. 상품명 찾기
            String productName = findProductNameByCode(pdfText, insuCd);
            if (productName == null) {
                log.warn("상품명을 찾을 수 없음: {}", insuCd);
                return getEmptyResult();
            }
            
            // 2. 사업방법 섹션에서 조건 추출
            Map<String, String> terms = extractTermsFromBusinessMethod(pdfText, productName, insuCd);
            
            log.info("사업방법서 파싱 완료: {} (신뢰도: {})", insuCd, evaluateConfidence(terms));
            return terms;
            
        } catch (Exception e) {
            log.error("사업방법서 파싱 오류: {}", e.getMessage(), e);
            return getEmptyResult();
        }
    }
    
    @Override
    public String getStrategyName() {
        return "Business Method";
    }
    
    @Override
    public int getPriority() {
        return 2; // Python OCR 다음으로 시도
    }
    
    @Override
    public boolean isAvailable() {
        return true; // 항상 사용 가능
    }
    
    @Override
    public int evaluateConfidence(Map<String, String> result) {
        if (result == null || result.isEmpty()) {
            return 0;
        }
        
        int score = 0;
        
        // 각 필드가 유효한지 확인
        if (isValidField(result.get("insuTerm"))) score += 25;
        if (isValidField(result.get("payTerm"))) score += 25;
        if (isValidField(result.get("ageRange"))) score += 25;
        if (isValidField(result.get("renew"))) score += 25;
        
        return score;
    }
    
    /**
     * PDF 텍스트 추출
     */
    private String extractPdfText(File pdfFile) throws Exception {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
    
    /**
     * 상품명 찾기
     */
    private String findProductNameByCode(String text, String insuCd) {
        // 패턴: "보험코드 다음에 상품명"
        String[] patterns = {
            insuCd + "\\s+([^\\n]+특약|[^\\n]+보험)",
            "\\[" + insuCd + "\\]\\s+([^\\n]+)",
            insuCd + "\\s*:\\s*([^\\n]+)"
        };
        
        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String productName = matcher.group(1).trim();
                log.debug("상품명 발견: {} → {}", insuCd, productName);
                return productName;
            }
        }
        
        return null;
    }
    
    /**
     * 사업방법서에서 조건 추출
     */
    private Map<String, String> extractTermsFromBusinessMethod(String text, String productName, String insuCd) {
        Map<String, String> terms = new LinkedHashMap<>();
        
        // 사업방법 섹션 추출
        String businessSection = extractBusinessMethodSection(text);
        if (businessSection == null) {
            log.warn("사업방법 섹션을 찾을 수 없음");
            return getEmptyResult();
        }
        
        // 상품명 주변에서 조건 추출
        terms = extractTermsAroundProductName(businessSection, productName);
        
        // 추가 정보
        terms.put("specialNotes", "사업방법서 기반 파싱");
        
        return terms;
    }
    
    /**
     * 사업방법 섹션 추출
     */
    private String extractBusinessMethodSection(String text) {
        String[] patterns = {
            "4\\.\\s*사업방법([\\s\\S]*?)(?:5\\.|$)",
            "사업방법상의\\s*내용([\\s\\S]*?)(?:주\\s*요\\s*내\\s*용|$)",
            "사업방법([\\s\\S]*?)(?:가입한도|$)"
        };
        
        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        return null;
    }
    
    /**
     * 상품명 주변에서 조건 추출
     */
    private Map<String, String> extractTermsAroundProductName(String section, String productName) {
        Map<String, String> terms = new LinkedHashMap<>();
        
        // 상품명 위치 찾기
        int nameIndex = section.indexOf(productName);
        if (nameIndex == -1) {
            return getEmptyResult();
        }
        
        // 상품명 전후 500자 범위에서 추출
        int start = Math.max(0, nameIndex - 200);
        int end = Math.min(section.length(), nameIndex + 500);
        String context = section.substring(start, end);
        
        // 보험기간 추출
        terms.put("insuTerm", extractInsuranceTerm(context));
        
        // 납입기간 추출
        terms.put("payTerm", extractPaymentTerm(context));
        
        // 가입나이 추출
        terms.put("ageRange", extractAgeRange(context));
        
        // 갱신여부 추출
        if (context.contains("갱신형")) {
            terms.put("renew", "갱신형");
        } else if (context.contains("비갱신형")) {
            terms.put("renew", "비갱신형");
        } else {
            terms.put("renew", "—");
        }
        
        return terms;
    }
    
    /**
     * 보험기간 추출
     */
    private String extractInsuranceTerm(String text) {
        Pattern pattern = Pattern.compile("보험기간[:\\s]*(종신|\\d+세만기|\\d+년만기)[,\\s]*(종신|\\d+세만기|\\d+년만기)?");
        Matcher matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            String term1 = matcher.group(1);
            String term2 = matcher.group(2);
            
            if (term2 != null && !term2.isEmpty()) {
                return term1 + ", " + term2;
            }
            return term1;
        }
        
        return "—";
    }
    
    /**
     * 납입기간 추출
     */
    private String extractPaymentTerm(String text) {
        Pattern pattern = Pattern.compile("납입기간[:\\s]*(\\d+년납)[,\\s]*(\\d+년납)?[,\\s]*(\\d+년납)?[,\\s]*(\\d+년납)?");
        Matcher matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            StringBuilder terms = new StringBuilder(matcher.group(1));
            for (int i = 2; i <= 4; i++) {
                String term = matcher.group(i);
                if (term != null && !term.isEmpty()) {
                    terms.append(", ").append(term);
                }
            }
            return terms.toString();
        }
        
        return "—";
    }
    
    /**
     * 가입나이 추출
     */
    private String extractAgeRange(String text) {
        Pattern pattern = Pattern.compile("가입나이[:\\s]*([^\\n]{10,100})");
        Matcher matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            String ageContent = matcher.group(1).trim();
            
            // "만"과 "세" 제거
            ageContent = ageContent.replaceAll("만", "");
            ageContent = ageContent.replaceAll("세", "");
            
            return ageContent;
        }
        
        return "—";
    }
    
    private boolean isValidField(String value) {
        return value != null && !value.isEmpty() && !value.equals("—");
    }
    
    private Map<String, String> getEmptyResult() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("insuTerm", "—");
        result.put("payTerm", "—");
        result.put("ageRange", "—");
        result.put("renew", "—");
        result.put("specialNotes", "사업방법서 파싱 실패");
        return result;
    }
}

