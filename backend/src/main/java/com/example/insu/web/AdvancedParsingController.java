package com.example.insu.web;

import com.example.insu.dto.UwCodeMappingData;
import com.example.insu.service.HybridParsingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 고급 파싱 기능을 위한 REST 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/advanced")
@RequiredArgsConstructor
public class AdvancedParsingController {
    
    private final HybridParsingService hybridParsingService;
    
    /**
     * 하이브리드 파싱으로 문서 처리
     */
    @PostMapping("/parse-document")
    public Map<String, Object> parseDocument(@RequestParam String fileName, 
                                           @RequestParam String docId) {
        log.info("===== 고급 파싱 API 호출 =====");
        log.info("파일명: {}, 문서ID: {}", fileName, docId);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // PDF 파일 경로 설정 (실제 환경에 맞게 수정 필요)
            String pdfDir = System.getProperty("user.dir") + "/insuPdf";
            File pdfFile = new File(pdfDir, fileName);
            
            if (!pdfFile.exists()) {
                result.put("success", false);
                result.put("error", "PDF 파일을 찾을 수 없습니다: " + fileName);
                return result;
            }
            
            // 하이브리드 파싱 실행
            List<UwCodeMappingData> mappingData = hybridParsingService.parseDocument(pdfFile, docId);
            
            result.put("success", true);
            result.put("docId", docId);
            result.put("fileName", fileName);
            result.put("totalCount", mappingData.size());
            result.put("mappingData", mappingData);
            
            log.info("고급 파싱 완료: {} 개 항목", mappingData.size());
            
        } catch (Exception e) {
            log.error("고급 파싱 실패: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", "파싱 실패: " + e.getMessage());
        }
        
        log.info("===== 고급 파싱 API 완료 =====");
        return result;
    }
    
    /**
     * 파싱 결과를 CSV 형식으로 내보내기
     */
    @GetMapping("/export-csv/{docId}")
    public String exportToCsv(@PathVariable String docId) {
        log.info("CSV 내보내기 요청: {}", docId);
        
        // 실제로는 데이터베이스에서 조회하거나 캐시된 결과 사용
        StringBuilder csv = new StringBuilder();
        
        // CSV 헤더
        csv.append("CODE,PRODUCT_NAME,PRODUCT_GROUP,TYPE_LABEL,MAIN_CODE,")
           .append("PERIOD_LABEL,PERIOD_VALUE,PERIOD_KIND,PAY_TERM,")
           .append("ENTRY_AGE_M,ENTRY_AGE_F,CLASS_TAG,SRC_FILE\n");
        
        // 샘플 데이터 (실제로는 데이터베이스 조회 결과)
        csv.append("21690,\"(무)실손의료비보험 (종합형, 비위험, 최초계약)\",주계약,최초계약,21690,")
           .append("종신,999,E,\"10년납, 15년납, 20년납, 30년납\",")
           .append("\"10년납(남:15~80,여:15~80)\",\"10년납(남:15~80,여:15~80)\",MAIN,")
           .append(docId).append("\n");
        
        csv.append("21704,\"(무)기본형 실손의료비보험 (급여 실손의료비)(갱신형) - 질병급여형\",선택특약,최초계약,21690,")
           .append("갱신형,999,R,전기납,\"15~80세\",\"15~80세\",A_OPTION,")
           .append(docId).append("\n");
        
        return csv.toString();
    }
    
    /**
     * 파싱 통계 조회
     */
    @GetMapping("/parsing-stats")
    public Map<String, Object> getParsingStats() {
        log.info("파싱 통계 조회");
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDocuments", 0);
        stats.put("totalCodes", 0);
        stats.put("mainContracts", 0);
        stats.put("riders", 0);
        stats.put("lastUpdated", System.currentTimeMillis());
        
        return stats;
    }
}
