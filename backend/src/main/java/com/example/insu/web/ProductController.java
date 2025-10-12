package com.example.insu.web;

import com.example.insu.dto.LimitInfo;
import com.example.insu.dto.PremiumCalcRequest;
import com.example.insu.dto.PremiumCalcResponse;
import com.example.insu.dto.ProductInfoResponse;
import com.example.insu.dto.UwCodeMappingData;
import com.example.insu.dto.ValidationResult;
import com.example.insu.service.ProductService;
import com.example.insu.service.UwCodeMappingValidationService;
import com.example.insu.service.UwMappingHybridParsingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {

  private final ProductService productService;
  private final UwCodeMappingValidationService uwMappingValidationService;
  private final UwMappingHybridParsingService uwMappingHybridParsingService;

  @GetMapping("/product/{insuCd}")
  public ProductInfoResponse product(@PathVariable String insuCd) {
    return productService.getProductInfo(insuCd);
  }

  @GetMapping("/limit/{insuCd}")
  public LimitInfo limit(@PathVariable String insuCd,
                         @RequestParam(required = false) Integer age) {
    return productService.getLimit(insuCd, age);
  }

  @PostMapping("/calc")
  public PremiumCalcResponse calc(@RequestBody PremiumCalcRequest req) {
    return productService.calcPremium(req);
  }

  @GetMapping("/data/check/{insuCd}")
  public Object checkData(@PathVariable String insuCd,
                          @RequestParam(required = false) Integer age,
                          @RequestParam(required = false) String payTerm) {
    return productService.checkDataAvailability(insuCd, age, payTerm);
  }

  @GetMapping("/premium/minmax/{insuCd}")
  public Object getMinMaxPremium(@PathVariable String insuCd,
                                @RequestParam(required = false) Integer age,
                                @RequestParam(required = false) String payTerm) {
    return productService.getMinMaxPremium(insuCd, age, payTerm);
  }

  @GetMapping("/contract/terms/{insuCd}")
  public Object getContractTerms(@PathVariable String insuCd) {
    return productService.getContractTerms(insuCd);
  }

  @GetMapping("/product/{mainCode}/related-codes")
  public Object getRelatedCodes(@PathVariable String mainCode) {
    log.info("===== 주계약 관련 코드 조회 API 호출 =====");
    log.info("주계약 코드: {}", mainCode);
    
    try {
      Object result = productService.getRelatedCodes(mainCode);
      log.info("관련 코드 조회 결과: {}", result);
      log.info("===== 주계약 관련 코드 조회 API 완료 =====");
      return result;
      
    } catch (Exception e) {
      log.error("주계약 관련 코드 조회 중 오류 발생", e);
      log.error("오류 상세 - 주계약: {}, 오류: {}", mainCode, e.getMessage());
      
      Map<String, Object> errorResult = new HashMap<>();
      errorResult.put("mainCode", mainCode);
      errorResult.put("relatedCodes", new ArrayList<>());
      errorResult.put("error", "관련 코드 조회 실패: " + e.getMessage());
      
      return errorResult;
    }
  }

  @GetMapping("/debug/query/{insuCd}")
  public Object debugQuery(@PathVariable String insuCd,
                          @RequestParam(required = false, defaultValue = "15") Integer age,
                          @RequestParam(required = false, defaultValue = "999") Integer insuTerm,
                          @RequestParam(required = false, defaultValue = "10") Integer payTerm) {
    return productService.debugQuery(insuCd, age, insuTerm, payTerm);
  }

  @GetMapping("/premium/calculate/{insuCd}")
  public Object calculatePremium(@PathVariable String insuCd,
                                @RequestParam(required = false, defaultValue = "15") Integer age,
                                @RequestParam(required = false, defaultValue = "10") Integer insuTerm,
                                @RequestParam(required = false, defaultValue = "10") Integer payTerm,
                                @RequestParam(required = false, defaultValue = "100") java.math.BigDecimal baseAmount) {
    return productService.calculatePremiumByAmount(insuCd, age, insuTerm, payTerm, baseAmount);
  }

  @GetMapping("/parse/python/{insuCd}")
  public Object parsePdfWithPython(@PathVariable String insuCd) {
    return productService.parsePdfWithPython(insuCd);
  }

  @GetMapping("/parse/hybrid/{insuCd}")
  public Object parsePdfHybrid(@PathVariable String insuCd) {
    return productService.parsePdfHybrid(insuCd);
  }

  @GetMapping("/premium/calculate-by-terms/{insuCd}")
  public Object calculatePremiumByTerms(@PathVariable String insuCd,
                                       @RequestParam(required = false, defaultValue = "15") Integer age,
                                       @RequestParam String insuTerm,
                                       @RequestParam String payTerm,
                                       @RequestParam(required = false, defaultValue = "100") java.math.BigDecimal baseAmount) {
    return productService.calculatePremiumByTerms(insuCd, age, insuTerm, payTerm, baseAmount);
  }

  // UW_CODE_MAPPING 관련 API 엔드포인트들
  
  /** UW_CODE_MAPPING 테이블에서 보험코드 기준 데이터 조회 */
  @GetMapping("/uw-mapping/{insuCd}")
  public java.util.List<UwCodeMappingData> getUwMappingData(@PathVariable String insuCd) {
    return uwMappingValidationService.getValidationDataByCode(insuCd);
  }
  
  /** UW_CODE_MAPPING 테이블에서 주계약 코드 기준 데이터 조회 */
  @GetMapping("/uw-mapping/main/{mainCode}")
  public java.util.List<UwCodeMappingData> getUwMappingDataByMainCode(@PathVariable String mainCode) {
    return uwMappingValidationService.getValidationDataByMainCode(mainCode);
  }
  
  /** UW_CODE_MAPPING 기반 검증 파싱 실행 */
  @GetMapping("/parse/uw-mapping/{insuCd}")
  public Object parseWithUwMapping(@PathVariable String insuCd) {
    try {
      java.io.File pdfFile = com.example.insu.util.PdfParser.findPdfForCode(
          productService.getPdfDir(), insuCd);
      if (pdfFile == null) {
        return java.util.Map.of("error", "PDF 파일을 찾을 수 없습니다: " + insuCd);
      }
      
      return uwMappingHybridParsingService.parseWithUwMappingValidation(pdfFile, insuCd);
    } catch (Exception e) {
      return java.util.Map.of("error", "UW_CODE_MAPPING 파싱 오류: " + e.getMessage());
    }
  }
  
  /** UW_CODE_MAPPING 통계 조회 */
  @GetMapping("/uw-mapping/statistics")
  public Object getUwMappingStatistics() {
    return uwMappingHybridParsingService.getUwMappingStatistics();
  }
  
  /** 파싱 결과와 UW_CODE_MAPPING 데이터 비교 검증 */
  @PostMapping("/uw-mapping/validate/{insuCd}")
  public ValidationResult validateWithUwMapping(@PathVariable String insuCd, 
                                               @RequestBody java.util.Map<String, String> parsedResult) {
    return uwMappingValidationService.validateWithUwMapping(insuCd, parsedResult);
  }
  
  /** 테스트용: 실손상품 매핑 테스트 */
  @GetMapping("/test/actual-loss/{mainCode}")
  public Object testActualLossMapping(@PathVariable String mainCode) {
    return productService.testActualLossMapping(mainCode);
  }
}
