package com.example.insu.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.example.insu.dto.UwCodeMappingData;

import java.util.*;
import java.util.regex.Pattern;

/**
 * LLM 프롬프트 템플릿 기반 고급 매핑 규칙 서비스
 */
@Slf4j
@Service
public class AdvancedMappingService {
    
    /**
     * 시리즈형 상품 매핑 (UW21385: 325/335/355 간편심사형)
     */
    public List<UwCodeMappingData> mapSeriesProduct(Map<String, String> parsedCodes, String docId) {
        log.info("===== 시리즈형 상품 매핑 시작: {} =====", docId);
        
        List<UwCodeMappingData> result = new ArrayList<>();
        Map<String, String> seriesMainCodes = new HashMap<>();
        
        // 시리즈별 주계약 코드 추출 (1종/2종)
        for (Map.Entry<String, String> entry : parsedCodes.entrySet()) {
            String code = entry.getKey();
            String name = entry.getValue();
            
            if (isSeriesMainContract(name)) {
                String series = extractSeries(name);
                String type = extractSeriesType(name); // 1종/2종
                String seriesKey = series + "_" + type;
                
                seriesMainCodes.put(seriesKey, code);
                log.info("시리즈 주계약 발견: {} -> {} ({})", code, name, seriesKey);
            }
        }
        
        // 시리즈별 특약 매핑
        for (Map.Entry<String, String> entry : parsedCodes.entrySet()) {
            String code = entry.getKey();
            String name = entry.getValue();
            
            if (isSeriesRider(name)) {
                String series = extractSeries(name);
                String type = extractSeriesType(name);
                String seriesKey = series + "_" + type;
                String mainCode = seriesMainCodes.get(seriesKey);
                
                if (mainCode != null) {
                    UwCodeMappingData data = createMappingData(code, name, mainCode, docId, "선택특약");
                    result.add(data);
                    log.info("시리즈 특약 매핑: {} -> {} (주계약: {})", code, name, mainCode);
                }
            }
        }
        
        log.info("시리즈형 상품 매핑 완료: {} 개 항목", result.size());
        return result;
    }
    
    /**
     * 고지형 상품 매핑 (UW21828: 7년/10년고지형)
     */
    public List<UwCodeMappingData> mapGuaranteedTypeProduct(Map<String, String> parsedCodes, String docId) {
        log.info("===== 고지형 상품 매핑 시작: {} =====", docId);
        
        List<UwCodeMappingData> result = new ArrayList<>();
        Map<String, String> guaranteedMainCodes = new HashMap<>();
        
        // 고지형별 주계약 코드 추출
        for (Map.Entry<String, String> entry : parsedCodes.entrySet()) {
            String code = entry.getKey();
            String name = entry.getValue();
            
            if (isGuaranteedMainContract(name)) {
                String guaranteedType = extractGuaranteedType(name); // 7년/10년
                guaranteedMainCodes.put(guaranteedType, code);
                log.info("고지형 주계약 발견: {} -> {} ({})", code, name, guaranteedType);
            }
        }
        
        // 고지형별 특약 매핑
        for (Map.Entry<String, String> entry : parsedCodes.entrySet()) {
            String code = entry.getKey();
            String name = entry.getValue();
            
            if (isGuaranteedRider(name)) {
                String guaranteedType = extractGuaranteedType(name);
                String mainCode = guaranteedMainCodes.get(guaranteedType);
                
                if (mainCode != null) {
                    UwCodeMappingData data = createMappingData(code, name, mainCode, docId, "선택특약");
                    
                    // 갱신형 특약인 경우 PERIOD_KIND = R로 설정
                    if (isRenewalRider(name)) {
                        data.setPeriodKind("R");
                        data.setPayTerm("전기납");
                    }
                    
                    result.add(data);
                    log.info("고지형 특약 매핑: {} -> {} (주계약: {})", code, name, mainCode);
                }
            }
        }
        
        log.info("고지형 상품 매핑 완료: {} 개 항목", result.size());
        return result;
    }
    
    /**
     * 다중상품 문서 매핑 (UW19771 등)
     */
    public List<UwCodeMappingData> mapMultiProductDocument(Map<String, String> parsedCodes, String docId) {
        log.info("===== 다중상품 문서 매핑 시작: {} =====", docId);
        
        List<UwCodeMappingData> result = new ArrayList<>();
        List<String> productBlocks = identifyProductBlocks(parsedCodes);
        
        for (String productBlock : productBlocks) {
            log.info("상품 블록 처리: {}", productBlock);
            
            // 각 블록별로 주계약과 특약 매핑
            Map<String, String> blockMainCodes = extractMainCodesForBlock(parsedCodes, productBlock);
            
            for (Map.Entry<String, String> entry : parsedCodes.entrySet()) {
                String code = entry.getKey();
                String name = entry.getValue();
                
                if (belongsToBlock(name, productBlock)) {
                    String mainCode = determineMainCodeForRider(name, blockMainCodes);
                    String productGroup = isMainContract(name) ? "주계약" : "선택특약";
                    
                    UwCodeMappingData data = createMappingData(code, name, mainCode, docId, productGroup);
                    result.add(data);
                }
            }
        }
        
        log.info("다중상품 문서 매핑 완료: {} 개 항목", result.size());
        return result;
    }
    
    /**
     * 복잡한 매핑 규칙 통합 처리
     */
    public List<UwCodeMappingData> processAdvancedMapping(Map<String, String> parsedCodes, String docId) {
        log.info("===== 고급 매핑 처리 시작: {} =====", docId);
        
        List<UwCodeMappingData> result = new ArrayList<>();
        
        // 문서 유형 감지
        DocumentType docType = detectDocumentType(parsedCodes, docId);
        log.info("감지된 문서 유형: {}", docType);
        
        switch (docType) {
            case SERIES_PRODUCT:
                result.addAll(mapSeriesProduct(parsedCodes, docId));
                break;
            case GUARANTEED_TYPE:
                result.addAll(mapGuaranteedTypeProduct(parsedCodes, docId));
                break;
            case MULTI_PRODUCT:
                result.addAll(mapMultiProductDocument(parsedCodes, docId));
                break;
            case TRADITIONAL:
            default:
                result.addAll(mapTraditionalProduct(parsedCodes, docId));
                break;
        }
        
        log.info("고급 매핑 처리 완료: {} 개 항목", result.size());
        return result;
    }
    
    // ===== 헬퍼 메서드들 =====
    
    private boolean isSeriesMainContract(String name) {
        return name != null && (
            name.contains("325") || name.contains("335") || name.contains("355")
        ) && name.contains("주계약");
    }
    
    private boolean isSeriesRider(String name) {
        return name != null && (
            name.contains("325") || name.contains("335") || name.contains("355")
        ) && name.contains("특약");
    }
    
    private String extractSeries(String name) {
        if (name.contains("325")) return "325";
        if (name.contains("335")) return "335";
        if (name.contains("355")) return "355";
        return "UNKNOWN";
    }
    
    private String extractSeriesType(String name) {
        if (name.contains("1종")) return "1종";
        if (name.contains("2종")) return "2종";
        return "UNKNOWN";
    }
    
    private boolean isGuaranteedMainContract(String name) {
        return name != null && (
            name.contains("7년") || name.contains("10년")
        ) && name.contains("기본형") && name.contains("미지급V2");
    }
    
    private boolean isGuaranteedRider(String name) {
        return name != null && (
            name.contains("7년") || name.contains("10년")
        ) && name.contains("특약");
    }
    
    private String extractGuaranteedType(String name) {
        if (name.contains("7년")) return "7년";
        if (name.contains("10년")) return "10년";
        return "UNKNOWN";
    }
    
    private boolean isRenewalRider(String name) {
        return name != null && name.contains("갱신형");
    }
    
    private List<String> identifyProductBlocks(Map<String, String> parsedCodes) {
        // 다중상품 문서에서 상품 블록 식별
        Set<String> blocks = new HashSet<>();
        
        for (String name : parsedCodes.values()) {
            if (name != null && name.contains("상품")) {
                String block = extractProductBlock(name);
                if (!block.equals("UNKNOWN")) {
                    blocks.add(block);
                }
            }
        }
        
        return new ArrayList<>(blocks);
    }
    
    private String extractProductBlock(String name) {
        // 상품 블록 식별 로직 (예: "상품A", "상품B" 등)
        Pattern pattern = Pattern.compile("상품([A-Z])");
        java.util.regex.Matcher matcher = pattern.matcher(name);
        if (matcher.find()) {
            return "상품" + matcher.group(1);
        }
        return "UNKNOWN";
    }
    
    private Map<String, String> extractMainCodesForBlock(Map<String, String> parsedCodes, String block) {
        Map<String, String> mainCodes = new HashMap<>();
        
        for (Map.Entry<String, String> entry : parsedCodes.entrySet()) {
            String code = entry.getKey();
            String name = entry.getValue();
            
            if (belongsToBlock(name, block) && isMainContract(name)) {
                mainCodes.put(code, code);
            }
        }
        
        return mainCodes;
    }
    
    private boolean belongsToBlock(String name, String block) {
        return name != null && name.contains(block);
    }
    
    private String determineMainCodeForRider(String riderName, Map<String, String> blockMainCodes) {
        // 특약의 주계약 결정 로직
        for (String mainCode : blockMainCodes.keySet()) {
            // 간단한 휴리스틱: 같은 블록의 첫 번째 주계약 사용
            return mainCode;
        }
        return "UNKNOWN";
    }
    
    private boolean isMainContract(String name) {
        return name != null && (
            name.contains("주계약") || 
            name.contains("최초계약") ||
            (!name.contains("특약") && !name.contains("부가"))
        );
    }
    
    private DocumentType detectDocumentType(Map<String, String> parsedCodes, String docId) {
        // 문서 유형 감지 로직
        for (String name : parsedCodes.values()) {
            if (name != null) {
                if (name.contains("325") || name.contains("335") || name.contains("355")) {
                    return DocumentType.SERIES_PRODUCT;
                }
                if (name.contains("7년") || name.contains("10년")) {
                    return DocumentType.GUARANTEED_TYPE;
                }
                if (name.contains("상품A") || name.contains("상품B")) {
                    return DocumentType.MULTI_PRODUCT;
                }
            }
        }
        
        return DocumentType.TRADITIONAL;
    }
    
    private List<UwCodeMappingData> mapTraditionalProduct(Map<String, String> parsedCodes, String docId) {
        List<UwCodeMappingData> result = new ArrayList<>();
        
        for (Map.Entry<String, String> entry : parsedCodes.entrySet()) {
            String code = entry.getKey();
            String name = entry.getValue();
            String productGroup = isMainContract(name) ? "주계약" : "선택특약";
            String mainCode = isMainContract(name) ? code : "21686"; // 기본 주계약
            
            UwCodeMappingData data = createMappingData(code, name, mainCode, docId, productGroup);
            result.add(data);
        }
        
        return result;
    }
    
    private UwCodeMappingData createMappingData(String code, String name, String mainCode, 
                                               String docId, String productGroup) {
        return UwCodeMappingData.builder()
            .srcFile(docId)
            .code(code)
            .productName(name)
            .mainCode(mainCode)
            .periodLabel("종신")
            .periodValue(999)
            .payTerm("10년납, 15년납, 20년납, 30년납")
            .entryAgeM("15~80세")
            .entryAgeF("15~80세")
            .productGroup(productGroup)
            .typeLabel("최초계약")
            .periodKind("E")
            .classTag(productGroup.equals("주계약") ? "MAIN" : "A_OPTION")
            .build();
    }
    
    private enum DocumentType {
        SERIES_PRODUCT,    // 시리즈형 (325/335/355)
        GUARANTEED_TYPE,   // 고지형 (7년/10년)
        MULTI_PRODUCT,     // 다중상품
        TRADITIONAL        // 일반상품
    }
}
