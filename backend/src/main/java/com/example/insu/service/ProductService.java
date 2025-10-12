package com.example.insu.service;

import com.example.insu.dto.*;
import com.example.insu.mapper.InsuMapper;
import com.example.insu.mapper.PremRateRow;
import com.example.insu.util.PdfParser;
import com.example.insu.util.PdfParser.Sections;
import lombok.RequiredArgsConstructor;
// import com.example.insu.service.PythonPdfService; // 사용되지 않음
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.nio.file.Paths;
import com.example.insu.util.LayoutStripper;
import com.example.insu.util.LimitTableExtractor;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

  private final InsuMapper insuMapper;
  private final PythonPdfService pythonPdfService;
  private final ImprovedHybridParsingService hybridParsingService; // Phase 1 개선: Caffeine Cache 적용
  private final UwMappingHybridParsingService uwMappingHybridParsingService; // UW_CODE_MAPPING 기반 검증
  private final UwCodeMappingValidationService uwMappingValidationService; // UW_CODE_MAPPING 검증 서비스

  @Value("${insu.pdf-dir}")
  private String pdfDir;

  /** PDF 디렉토리 경로 반환 (다른 서비스에서 사용) */
  public String getPdfDir() {
    return pdfDir;
  }

  /** ① 상품 정보(이름/사업방법 요약/정계산 가능여부) - 조합별 행 생성 */
  public ProductInfoResponse getProductInfo(String insuCd) {
    log.info("[product] try insuCd={}, pdfDir={}", insuCd, pdfDir);

    // 가드#1: 정계산 가능여부는 매퍼 시그니처 차이 가능성 때문에 방어적으로
    boolean calcAvail = false;

    try {
      // 존재 여부만 체크하는 전용 메서드로 교체 
      calcAvail = insuMapper.countPremRateByInsuCd(insuCd) > 0;
    } catch (Exception e) {
      // 매퍼 구성에 따라 실패해도 기능 영향 없도록 로깅만
      log.debug("[product] prem rate check failed for {}: {}", insuCd, e.toString());
    }

    Path dir = Paths.get(pdfDir);
    File pdf = PdfParser.findPdfForCode(dir, insuCd);   // 시그니처 유지
    if (pdf == null) {
      log.warn("[product] no PDF matched for insuCd={} under {}", insuCd, pdfDir);
      return ProductInfoResponse.builder()
          .insuCd(insuCd).calcAvailable(calcAvail)
          .message("질문에 해당하는 PDF 내용이 없습니다")
          .build();
    }

    log.info("[product] matched PDF={} for insuCd={}", pdf.getAbsolutePath(), insuCd);

    try {
      String text = PdfParser.readAllText(pdf);
      PdfParser.Sections sec = PdfParser.splitSections(text);

      // 이름 찾기: 일반표 → 4열(10P) → 라인형 → 퍼지(백업)
      Map<String,String> code2name = new LinkedHashMap<>();
      code2name.putAll(PdfParser.parseCodeTable(sec.block3));
      if (!code2name.containsKey(insuCd))
        code2name.putAll(PdfParser.parseCodeTableFourCols(sec.block3));
      String name = code2name.get(insuCd);
      if (name == null)
        name = PdfParser.fuzzyFindNameByCode(sec.block3 != null ? sec.block3 : text, insuCd);
      log.info("[product] mapped name for {} => {}", insuCd, name);

      // Python 파싱으로 보험기간, 납입기간, 가입나이 정보 추출
      Map<String,String> t = parseTermsWithPython(pdf, insuCd);

      // 보험기간과 납입기간의 모든 조합 생성
      List<PolicyTerms> termsList = generateTermCombinations(t);

      return ProductInfoResponse.builder()
          .insuCd(insuCd).name(name).type(guessTypeByCode(insuCd))
          .terms(termsList).calcAvailable(calcAvail)
          .message((name==null) ? "PDF에 코드가 있으나 명칭 추출 실패" : null)
          .build();

    } catch (Exception e) {
      log.error("[product] PDF parse error for {}: {}", insuCd, e.toString());
      return ProductInfoResponse.builder()
          .insuCd(insuCd).calcAvailable(calcAvail)
          .message("PDF 파싱 중 오류: " + e.getMessage())
          .build();
    }
  }

  /** ② 가입한도(연령대 반영) */
  public LimitInfo getLimit(String insuCd, Integer age) {
    File pdf = PdfParser.findPdfForCode(Path.of(pdfDir), insuCd);
    if (pdf == null) {
      return LimitInfo.builder()
          .insuCd(insuCd)
          .message("질문에 해당하는 PDF 내용이 없습니다")
          .build();
    }

    try {
      String text = PdfParser.readAllText(pdf);
      PdfParser.Sections sec = PdfParser.splitSections(text);

      // [로그] 36p 덤프 (pdfbox 3.x 대응 readPageText 사용 가정)
      try {
        String p36 = PdfParser.readPageText(pdf, 36);
        log.debug("[limit][page36.head] {}", p36.substring(0, Math.min(600, p36.length())).replace("\n","\\n"));
        if (p36.length() > 600) {
          log.debug("[limit][page36.tail] {}", p36.substring(Math.max(0, p36.length()-600)).replace("\n","\\n"));
        }
      } catch (Exception e) {
        log.debug("[limit] page36 dump failed: {}", e.toString());
      }

      // 1) 코드→명칭
      Map<String,String> code2name = new LinkedHashMap<>();
      code2name.putAll(PdfParser.parseCodeTable(sec.block3));
      if (!code2name.containsKey(insuCd)) {
        code2name.putAll(PdfParser.parseCodeTableFourCols(sec.block3)); // 있으면 사용
      }
      String name = code2name.get(insuCd);
      if (name == null) {
        name = PdfParser.fuzzyFindNameByCode(sec.block3 != null ? sec.block3 : text, insuCd);
      }
      if (name == null) {
        return LimitInfo.builder()
            .insuCd(insuCd)
            .message("PDF에 코드가 있으나 명칭을 찾지 못했습니다")
            .build();
      }

      final int targetAge = (age != null ? age : 15);

      // 2) 한도 수집: 레이아웃 → 구조 → 헤더열 → 느슨한 정규식
      PdfParser.MinMaxLimit mm = null;

      // 2-1) 좌표 기반(레이아웃) 최우선
      mm = fetchMinMaxFromPdf(pdf, name, targetAge);
      if (isEmptyLimit(mm)) {
        // 2-2) 구조적 AgeBand
        try {
          PdfParser.AgeBandLimit abl =
              PdfParser.parseAgeBandLimitByNameAndAge(sec.block5 != null ? sec.block5 : text, name, targetAge);
          if (abl != null && (abl.minWon != null || abl.maxWon != null)) {
            mm = new PdfParser.MinMaxLimit(abl.minWon, abl.maxWon, null, null, null, abl.matchedLine);
          }
        } catch (Exception ignore) {}
      }
      if (isEmptyLimit(mm)) {
        // 2-3) 헤더열 정렬 방식
        try {
          PdfParser.AgeBandLimit abl2 =
              PdfParser.parseAgeBandLimitByHeaderColumns(sec.block5 != null ? sec.block5 : text, name, targetAge);
          if (abl2 != null && (abl2.minWon != null || abl2.maxWon != null)) {
            mm = new PdfParser.MinMaxLimit(abl2.minWon, abl2.maxWon, null, null, null, abl2.matchedLine);
          }
        } catch (Exception ignore) {}
      }
      if (isEmptyLimit(mm)) {
        // 2-4) 느슨한 라인 정규식 폴백
        mm = PdfParser.parseMinMaxLooseByName(sec.block5 != null ? sec.block5 : text, name);
      }
      log.debug("[limit 1] nameRaw='{}', nameNorm='{}'", name, PdfParser.normalizeTitle(name));

      if (mm != null) {
        log.debug("[limit 1] matchedLine='{}'", mm.matchedLine);
      }
      // 4) UW21239 유형(단순 한도표) 폴백
      if (isEmptyLimit(mm)) {
        String nameNorm = PdfParser.normalizeTitle(name);
        PdfParser.MinMaxLimit simple =
            PdfParser.parseSimpleLimitByName(sec.block5 != null ? sec.block5 : text, nameNorm);
        if (simple != null && (simple.minWon != null || simple.maxWon != null)) {
          mm = simple;
          log.debug("[limit][simple] row='{}'", simple.matchedLine);
        }
      }

      log.debug("[limit 2] nameRaw='{}', nameNorm='{}'", name, PdfParser.normalizeTitle(name));
      
      if (mm != null) {
        log.debug("[limit 2] matchedLine='{}'", mm.matchedLine);
      }

      // 3) 최종 판정
      if (isEmptyLimit(mm)) {
        log.debug("[limit] all strategies failed for name='{}'", name);
        return LimitInfo.builder()
            .insuCd(insuCd).name(name)
            .message("가입한도 내용이 없습니다")
            .build();
      }

      // 4) 표시 문자열
      String display;
      if (mm != null) {
        if (mm.minWon != null && mm.maxWon != null) {
          display = "최소 " + prettyKR(mm.minWon) + " ~ 최대 " + prettyKR(mm.maxWon);
        } else if (mm.maxWon != null) {
          display = "최대 " + prettyKR(mm.maxWon);
        } else if (mm.minWon != null) {
          display = "최소 " + prettyKR(mm.minWon);
        } else {
          display = "—";
        }
      } else {
        display = "—";
      }

      if (mm != null) {
        log.debug("[limit] matched line='{}', min={}, max={}", mm.matchedLine, mm.minWon, mm.maxWon);
        
        // 5) 리턴 (mm가 null이 아닌 경우)
        return LimitInfo.builder()
            .insuCd(insuCd).name(name)
            .minWon(mm.minWon).maxWon(mm.maxWon)
            .display(display)
            .build();
      } else {
        log.debug("[limit] mm이 null이므로 기본값 반환");
        return LimitInfo.builder()
            .insuCd(insuCd).name(name)
            .display(display)
            .build();
      }

    } catch (Exception e) {
      return LimitInfo.builder()
          .insuCd(insuCd)
          .message("PDF 파싱 중 오류: " + e.getMessage())
          .build();
    }
  }


  /* ===== 내부 유틸 ===== */
  private PdfParser.MinMaxLimit fetchMinMaxFromPdf(File pdf, String productName, Integer ageOrNull) {
    try {
      // 1) "가입한도" 페이지 탐색 (1~80p 내에서 첫 매치)
      List<LayoutStripper.Line> linesOfHitPage = null;
      @SuppressWarnings("unused")
      int hitPage = -1;
      for (int p = 1; p <= 80; p++) {
        var ls = LayoutStripper.readPageLines(pdf, p);
        if (ls.stream().anyMatch(l -> l.text.contains("가입한도"))) {
          linesOfHitPage = ls;
          hitPage = p;
          break;
        }
      }
      if (linesOfHitPage == null) return null;

      // 2) 헤더 라인 잡기
      LayoutStripper.Line header = linesOfHitPage.stream()
          .filter(l -> l.text.contains("60세") && l.text.contains("최저"))
          .findFirst().orElse(null);
      if (header == null) {
        // 헤더 대체: "가입한도" 다음 라인을 헤더로 보는 등 보완 가능
        header = linesOfHitPage.stream()
            .filter(l -> l.text.contains("가입단위") || l.text.contains("가입한도"))
            .findFirst().orElse(null);
      }
      if (header == null) {
        log.debug("헤더 라인을 찾을 수 없음");
        return null;
      }

      Map<String, Float> anchors = LimitTableExtractor.detectHeaderAnchors(header);
      if (anchors.isEmpty()) return null;

      // 3) 대상 행(부분일치) 찾기: 상품명 정규화 후 "주계약/다사랑3N5" 등 키워드 매칭
      final String normTarget = LimitTableExtractor.normalizeName(productName);
      LayoutStripper.Line row = linesOfHitPage.stream()
          .filter(l -> {
            String s = LimitTableExtractor.normalizeName(l.text);
            // 주계약/특약 공통 처리 가능. 필요 시 키워드 늘리세요.
            // 예) 다사랑3N5, 사망보장, 재해사망 …
            boolean hitName = s.contains(normTarget) || s.contains("다사랑3N5");
            boolean looksRow = s.contains("주계약") || s.contains("보장") || s.contains("간편");
            return hitName && looksRow;
          })
          .findFirst().orElse(null);

      if (row == null) return null;

      // 4) 나이에 따른 밴드 결정
      String band;
      Integer age = ageOrNull;
      if (age == null) age = 60; // 기본값
      if (age <= 60) band = "60세이하";
      else if (age <= 65) band = "65세이하";
      else if (age <= 70) band = "70세이하";
      else band = "80세이하";

      // 5) 단위 힌트 (표 본문에 "(가입단위 : 만)" 이 문구가 흔함)
      String unitHint = "만";

      // 6) 좌표기반 슬라이스에서 값 추출
      BigDecimal maxBD = LimitTableExtractor.extractAtBand(row, band, anchors, unitHint);
      BigDecimal minBD = LimitTableExtractor.extractAtBand(row, "최저", anchors, unitHint);

      if (maxBD != null || minBD != null) {
        String matched = row.text;
        return new PdfParser.MinMaxLimit(minBD, maxBD, null, null, null, matched);
      }
    } catch (Exception e) {
      log.debug("[limit][layout] error {}", e.toString());
    }
    return null;
  }


  @SuppressWarnings("unused")
  private String buildDisplay(BigDecimal min, BigDecimal max) {
    if (min != null && max != null) return "최소 " + prettyKR(min) + " ~ 최대 " + prettyKR(max);
    if (max != null) return "최대 " + prettyKR(max);
    if (min != null) return "최소 " + prettyKR(min);
    return null;
  }

  private static String prettyKR(BigDecimal v) {
    long won = v.longValue();
    long eok = won / 100_000_000L;
    long rest = won % 100_000_000L;
    long man = rest / 10_000L;

    StringBuilder sb = new StringBuilder();
    if (eok > 0) sb.append(eok).append("억");
    if (man > 0 && eok == 0) {
      if (sb.length() > 0) sb.append(" ");
      sb.append(man).append("만");
    }
    if (sb.length() == 0) sb.append(String.format("%,d원", won)); else sb.append("원");
    return sb.toString();
  }

  private String guessTypeByCode(String insuCd) {
    char c = insuCd.charAt(0);
    return (c=='1' || c=='2') ? "주계약" : "특약";
  }

  // 4.사업방법 파싱이 프로젝트마다 달라 방어적으로 키만 맞춰줌
  @SuppressWarnings("unused")
  private Map<String,String> parseTermsSafe(String block4) {
    Map<String,String> t = new LinkedHashMap<>();
    if (block4 == null) return t;

    // 아주 느슨한 키워드 매핑(필요시 정교화)
    t.put("renew", PdfParser.findFirstRegex(block4, "(\\d+\\s*년\\s*갱신[^\\n]*)"));
    t.put("ageRange", PdfParser.findFirstRegex(block4, "(가입나이[^\\n]*)"));
    t.put("insuTerm", PdfParser.findFirstRegex(block4, "(보험기간[^\\n]*)"));
    t.put("payTerm",  PdfParser.findFirstRegex(block4, "(납입기간[^\\n]*)|((전기납)[^\\n]*)"));
    t.put("specialNotes", PdfParser.findFirstRegex(block4, "(특이사항[^\\n]*)|(최종\\s*100세[^\\n]*)|(주계약 기간 초과 불가[^\\n]*)"));
    return t;
  }


  /* ===== 정계산(요율) – 기존 로직 유지 예시 ===== */

  public PremiumCalcResponse calcPremium(PremiumCalcRequest req) {
    if (req.getInsuCd() == null || req.getGender() == null
        || req.getAge() == null || req.getInsuTerm() == null) {
      return baseResp(req).message("필수 값 누락(insuCd, gender, age, insuTerm)").build();
    }
    int payTerm = Boolean.TRUE.equals(req.getJeongi()) ? req.getInsuTerm()
        : (req.getPayTerm() != null ? req.getPayTerm() : req.getInsuTerm());

    if (req.getAmountManwon() == null) {
      return baseResp(req).message("계약금액(만원)이 없습니다").build();
    }

    // 요율/기준금액 조회(프로젝트의 매퍼 시그니처에 맞춰 사용)
    PremRateRow row = insuMapper.selectPremRate(
        req.getInsuCd(), req.getAge(), req.getInsuTerm(), payTerm);
    if (row == null) {
      return baseResp(req).message("요율 데이터가 없습니다(RVT_PREM_RATE)").build();
    }
    BigDecimal stnd = nz(row.getStndAmt());
    BigDecimal rate = "M".equalsIgnoreCase(req.getGender())
        ? nz(row.getManRate()) : nz(row.getFmlRate());
    if (stnd.compareTo(BigDecimal.ZERO) == 0) return baseResp(req).message("기준구성금액이 0입니다").build();
    if (rate.compareTo(BigDecimal.ZERO) == 0) return baseResp(req).message("성별 요율이 0입니다").build();

    BigDecimal amountWon = BigDecimal.valueOf(req.getAmountManwon()).multiply(BigDecimal.valueOf(10_000L));
    BigDecimal premiumWon = rate.multiply(amountWon).divide(stnd, 0, RoundingMode.HALF_UP);

    return baseResp(req)
        .insuTerm(req.getInsuTerm())
        .payTerm(payTerm)
        .amountWon(amountWon)
        .stdAmount(stnd)
        .rate(rate)
        .premiumWon(premiumWon)
        .message(null)
        .build();
  }

  private static boolean isEmptyLimit(PdfParser.MinMaxLimit m) {
    return m == null
        || (m.minWon == null && m.maxWon == null && m.perUnitWon == null);
  }

  private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

  private PremiumCalcResponse.PremiumCalcResponseBuilder baseResp(PremiumCalcRequest req) {
    return PremiumCalcResponse.builder()
        .insuCd(req.getInsuCd())
        .gender(req.getGender())
        .age(req.getAge())
        .insuTerm(req.getInsuTerm())
        .payTerm(Boolean.TRUE.equals(req.getJeongi()) ? req.getInsuTerm() : req.getPayTerm())
        .amountManwon(req.getAmountManwon());
  }

  /** ③ 데이터 존재 여부 검증 */
  public Map<String, Object> checkDataAvailability(String insuCd, Integer age, String payTerm) {
    Map<String, Object> result = new LinkedHashMap<>();
    List<String> errors = new ArrayList<>();
    
    // 1) 준비금키 검증
    boolean rsvKeyExists = insuMapper.countRsvKeyByInsuCd(insuCd) > 0;
    result.put("rsvKey", rsvKeyExists ? "Y" : "N");
    if (!rsvKeyExists) {
      errors.add("준비금키 데이터 없음");
    }
    
    // 2) 준비금 검증 (간단 버전 - 실제로는 복잡한 조건 검증 필요)
    boolean rsvRateExists = insuMapper.countRsvRateByInsuCd(insuCd) > 0;
    result.put("rsvRate", rsvRateExists ? "Y" : "N");
    if (!rsvRateExists) {
      errors.add("준비금 데이터 없음");
    }
    
    // 3) 보험료 검증
    boolean premRateExists = insuMapper.countPremRateByInsuCd(insuCd) > 0;
    result.put("premRate", premRateExists ? "Y" : "N");
    if (!premRateExists) {
      errors.add("보험료 데이터 없음");
    }
    
    result.put("errors", errors);
    return result;
  }

  /** ④ MIN/MAX 보험료 계산 */
  public Map<String, Object> getMinMaxPremium(String insuCd, Integer age, String payTerm) {
    Map<String, Object> result = new LinkedHashMap<>();
    List<String> errors = new ArrayList<>();
    
    try {
      // 기본 나이 설정
      int targetAge = age != null ? age : 15;
      
      // 보험료 데이터 조회 (간단 버전 - 실제로는 복잡한 조건 필요)
      PremRateRow premRate = insuMapper.selectPremRate(insuCd, targetAge, 10, 10);
      
      if (premRate == null) {
        errors.add(targetAge + "세, 보험기간 10, 납입기간 10 최소 or 최대 데이터 없음");
        result.put("errors", errors);
        return result;
      }
      
      // MIN/MAX 계산 (단일 레코드 기준)
      BigDecimal minMan = BigDecimal.ZERO;
      BigDecimal maxMan = BigDecimal.ZERO;
      BigDecimal minFml = BigDecimal.ZERO;
      BigDecimal maxFml = BigDecimal.ZERO;
      
      if (premRate.getStndAmt() != null) {
        BigDecimal manPremium = premRate.getStndAmt().multiply(nz(premRate.getManRate()));
        BigDecimal fmlPremium = premRate.getStndAmt().multiply(nz(premRate.getFmlRate()));
        
        minMan = manPremium;
        maxMan = manPremium;
        minFml = fmlPremium;
        maxFml = fmlPremium;
      }
      
      result.put("manMin", minMan);
      result.put("manMax", maxMan);
      result.put("fmlMin", minFml);
      result.put("fmlMax", maxFml);
      
    } catch (Exception e) {
      errors.add("보험료 계산 오류: " + e.getMessage());
    }
    
    result.put("errors", errors);
    return result;
  }

  /** ⑤ 계약조건설명 */
  public Map<String, Object> getContractTerms(String insuCd) {
    Map<String, Object> result = new LinkedHashMap<>();
    List<String> notes = new ArrayList<>();
    
    // PDF에서 계약조건 관련 정보 추출 (간단 버전)
    // 실제로는 PDF 파싱을 통해 특이사항, 필수가입특약, 주의사항 등을 추출해야 함
    
    // 예시 데이터
    notes.add("본 상품은 갱신형 상품입니다.");
    notes.add("필수가입특약: 암진단특약");
    notes.add("주의사항: 보험료는 나이에 따라 달라질 수 있습니다.");
    
    result.put("notes", notes);
    result.put("insuCd", insuCd);
    
    return result;
  }

  /** 테스트용: 실손상품 매핑 테스트 */
  public Map<String, Object> testActualLossMapping(String mainCode) {
    Map<String, Object> result = new LinkedHashMap<>();
    
    try {
      boolean isActualLoss = isActualLossProduct(mainCode);
      String riskLevel = extractRiskLevel(mainCode);
      String contractType = extractContractType(mainCode);
      
      List<String> basicCodes = getBasicActualLossCodes(contractType, riskLevel);
      List<String> specialCodes = getSpecialActualLossCodes(contractType, riskLevel);
      
      result.put("mainCode", mainCode);
      result.put("isActualLoss", isActualLoss);
      result.put("riskLevel", riskLevel);
      result.put("contractType", contractType);
      result.put("basicCodes", basicCodes);
      result.put("specialCodes", specialCodes);
      
    } catch (Exception e) {
      result.put("error", e.getMessage());
    }
    
    return result;
  }

  /** ⑥ 주계약별 관련 코드 조회 - 실손상품 위험도별 분기 지원 */
  public Map<String, Object> getRelatedCodes(String mainCode) {
    Map<String, Object> result = new LinkedHashMap<>();
    List<Map<String, Object>> relatedCodes = new ArrayList<>();
    
    try {
      log.info("주계약 코드 {}에 대한 관련 코드 조회 시작", mainCode);
      
      // 실손상품 주계약 코드인지 확인 및 위험도별 분기
      boolean isActualLoss = isActualLossProduct(mainCode);
      log.info("주계약 코드 {} 실손상품 여부: {}", mainCode, isActualLoss);
      
      if (isActualLoss) {
        log.info("실손상품 주계약 코드 감지: {}", mainCode);
        relatedCodes = getActualLossRelatedCodes(mainCode);
      } else {
        log.info("기존 상품 주계약 코드: {}", mainCode);
        // 기존 다사랑암보험 관련 코드들
        relatedCodes = getTraditionalRelatedCodes(mainCode);
      }
      
      result.put("mainCode", mainCode);
      result.put("relatedCodes", relatedCodes);
      log.info("주계약 코드 {}에 대한 관련 코드 {} 개 조회 완료", mainCode, relatedCodes.size());
      
    } catch (Exception e) {
      log.error("관련 코드 조회 실패: {}", e.getMessage(), e);
      result.put("error", "관련 코드 조회 실패: " + e.getMessage());
    }
    
    return result;
  }
  
  /** 실손상품 주계약 코드인지 확인 */
  private boolean isActualLossProduct(String mainCode) {
    // 실손상품 주계약 코드 범위: 21690-21702 (일반계약), 21712 (태아계약)
    boolean result = (mainCode.matches("2169[0-9]") || mainCode.matches("2170[0-2]") || "21712".equals(mainCode));
    log.info("실손상품 여부 확인: {} -> {}", mainCode, result);
    return result;
  }
  
  /** 실손상품 위험도별 관련 코드 조회 */
  private List<Map<String, Object>> getActualLossRelatedCodes(String mainCode) {
    List<Map<String, Object>> relatedCodes = new ArrayList<>();
    
    // 주계약 코드에서 위험도와 계약유형 추출
    String riskLevel = extractRiskLevel(mainCode);
    String contractType = extractContractType(mainCode);
    
    log.info("주계약 {} 분석: 위험도={}, 계약유형={}", mainCode, riskLevel, contractType);
    
    // 기본형 실손의료비보험 코드들
    List<String> basicCodes = getBasicActualLossCodes(contractType, riskLevel);
    log.info("기본형 코드들: {}", basicCodes);
    for (String code : basicCodes) {
      relatedCodes.add(createCodeInfo(code, getProductNameByCode(code), mainCode));
    }
    
    // 실손의료비특약 코드들
    List<String> specialCodes = getSpecialActualLossCodes(contractType, riskLevel);
    log.info("특약 코드들: {}", specialCodes);
    for (String code : specialCodes) {
      relatedCodes.add(createCodeInfo(code, getProductNameByCode(code), mainCode));
    }
    
    log.info("총 {} 개의 관련 코드 생성됨", relatedCodes.size());
    return relatedCodes;
  }
  
  /** 주계약 코드에서 위험도 추출 */
  private String extractRiskLevel(String mainCode) {
    // 21690, 21692, 21694 -> 비위험, 중위험, 고위험 (종합형)
    // 21696 -> 질병형 (위험도 구분 없음)
    // 21698, 21700, 21702 -> 비위험, 중위험, 고위험 (상해형)
    switch (mainCode) {
      case "21690": case "21691": case "21698": case "21699": return "비위험";
      case "21692": case "21693": case "21700": case "21701": return "중위험";
      case "21694": case "21695": case "21702": case "21703": return "고위험";
      case "21696": case "21697": case "21712": return "질병형"; // 질병형은 위험도 구분 없음
      default: return "비위험";
    }
  }
  
  /** 주계약 코드에서 계약유형 추출 */
  private String extractContractType(String mainCode) {
    // 짝수: 최초계약, 홀수: 갱신계약
    int code = Integer.parseInt(mainCode);
    return (code % 2 == 0) ? "최초" : "갱신";
  }
  
  /** 기본형 실손의료비보험 코드 조회 */
  private List<String> getBasicActualLossCodes(String contractType, String riskLevel) {
    List<String> codes = new ArrayList<>();
    
    // 질병급여형: 항상 동일 (21704/21705, 21713)
    if ("질병형".equals(riskLevel)) {
      codes.add("21713"); // 태아계약
    } else if ("최초".equals(contractType)) {
      codes.add("21704"); // 질병급여형
    } else {
      codes.add("21705"); // 질병급여형 갱신
    }
    
    // 상해급여형: 위험도별 분기
    if ("질병형".equals(riskLevel)) {
      codes.add("21714"); // 태아계약 상해급여형
    } else {
      String baseCode = "최초".equals(contractType) ? "21706" : "21707";
      switch (riskLevel) {
        case "비위험":
          codes.add(baseCode);
          break;
        case "중위험":
          codes.add(String.valueOf(Integer.parseInt(baseCode) + 2));
          break;
        case "고위험":
          codes.add(String.valueOf(Integer.parseInt(baseCode) + 4));
          break;
      }
    }
    
    return codes;
  }
  
  /** 실손의료비특약 코드 조회 */
  private List<String> getSpecialActualLossCodes(String contractType, String riskLevel) {
    List<String> codes = new ArrayList<>();
    
    if ("질병형".equals(riskLevel)) {
      // 태아계약 특약
      codes.addAll(Arrays.asList("79583", "79584", "79585"));
    } else {
      // 일반계약 특약
      String baseCode = "최초".equals(contractType) ? "79569" : "79570";
      
      // 질병비급여형
      codes.add(baseCode);
      
      // 상해비급여형 (위험도별)
      codes.add(String.valueOf(Integer.parseInt(baseCode) + 2));
      if (!"비위험".equals(riskLevel)) {
        codes.add(String.valueOf(Integer.parseInt(baseCode) + 4));
      }
      
      // 3대비급여형 (위험도별)
      codes.add(String.valueOf(Integer.parseInt(baseCode) + 8));
      if (!"비위험".equals(riskLevel)) {
        codes.add(String.valueOf(Integer.parseInt(baseCode) + 10));
      }
    }
    
    return codes;
  }
  
  /** 기존 다사랑암보험 관련 코드들 */
  private List<Map<String, Object>> getTraditionalRelatedCodes(String mainCode) {
    List<Map<String, Object>> relatedCodes = new ArrayList<>();
    
    // 21686에 속한 특약 코드들 정의
    Map<String, String[]> mainCodeToRelatedCodes = new HashMap<>();
    
    // 21686에 속한 특약 코드들
    mainCodeToRelatedCodes.put("21686", new String[]{
      "79525", "79527", "79957", "81880", "81817", "81819", 
      "83180", "83192", "81795", "81797", "81799", "81801",
      "81803", "81805", "81807", "81809", "81811", "81813",
      "81815", "81825", "81827", "81974", "82020", "82022",
      "82057", "83182", "83184", "83186"
    });
    
    // 21687에 속한 특약 코드들 (21686과 동일)
    mainCodeToRelatedCodes.put("21687", new String[]{
      "79525", "79527", "79957", "81880", "81817", "81819", 
      "83180", "83192", "81795", "81797", "81799", "81801",
      "81803", "81805", "81807", "81809", "81811", "81813",
      "81815", "81825", "81827", "81974", "82020", "82022",
      "82057", "83182", "83184", "83186"
    });
    
    String[] codes = mainCodeToRelatedCodes.get(mainCode);
    if (codes != null) {
      for (String code : codes) {
        relatedCodes.add(createCodeInfo(code, getProductNameByCode(code), mainCode));
      }
    }
    
    return relatedCodes;
  }
  
  /** 코드 정보 객체 생성 */
  private Map<String, Object> createCodeInfo(String insuCd, String name, String mainCode) {
    Map<String, Object> codeInfo = new HashMap<>();
    codeInfo.put("insuCd", insuCd);
    codeInfo.put("name", name);
    codeInfo.put("mainCode", mainCode);
    return codeInfo;
  }

  private String getProductNameByCode(String code) {
    // 코드별 상품명 매핑 (실제로는 DB에서 조회해야 함)
    Map<String, String> codeToName = new HashMap<>();
    
    // 실손상품 코드들
    codeToName.put("21704", "(무)기본형 실손의료비보험 (급여 실손의료비)(갱신형) - 질병급여형");
    codeToName.put("21705", "(무)기본형 실손의료비보험 (급여 실손의료비)(갱신형) - 질병급여형 갱신");
    codeToName.put("21706", "(무)기본형 실손의료비보험 (급여 실손의료비)(갱신형) - 상해급여형 비위험");
    codeToName.put("21707", "(무)기본형 실손의료비보험 (급여 실손의료비)(갱신형) - 상해급여형 비위험 갱신");
    codeToName.put("21708", "(무)기본형 실손의료비보험 (급여 실손의료비)(갱신형) - 상해급여형 중위험");
    codeToName.put("21709", "(무)기본형 실손의료비보험 (급여 실손의료비)(갱신형) - 상해급여형 중위험 갱신");
    codeToName.put("21710", "(무)기본형 실손의료비보험 (급여 실손의료비)(갱신형) - 상해급여형 고위험");
    codeToName.put("21711", "(무)기본형 실손의료비보험 (급여 실손의료비)(갱신형) - 상해급여형 고위험 갱신");
    codeToName.put("21713", "(무)기본형 실손의료비보험 (급여 실손의료비)(갱신형) - 질병급여형 태아계약");
    codeToName.put("21714", "(무)기본형 실손의료비보험 (급여 실손의료비)(갱신형) - 상해급여형 태아계약");
    
    codeToName.put("79569", "(무)실손의료비특약 (비급여 실손의료비)(갱신형) - 질병비급여형");
    codeToName.put("79570", "(무)실손의료비특약 (비급여 실손의료비)(갱신형) - 질병비급여형 갱신");
    codeToName.put("79571", "(무)실손의료비특약 (비급여 실손의료비)(갱신형) - 상해비급여형 비위험");
    codeToName.put("79572", "(무)실손의료비특약 (비급여 실손의료비)(갱신형) - 상해비급여형 비위험 갱신");
    codeToName.put("79573", "(무)실손의료비특약 (비급여 실손의료비)(갱신형) - 상해비급여형 중위험");
    codeToName.put("79574", "(무)실손의료비특약 (비급여 실손의료비)(갱신형) - 상해비급여형 중위험 갱신");
    codeToName.put("79575", "(무)실손의료비특약 (비급여 실손의료비)(갱신형) - 상해비급여형 고위험");
    codeToName.put("79576", "(무)실손의료비특약 (비급여 실손의료비)(갱신형) - 상해비급여형 고위험 갱신");
    codeToName.put("79577", "(무)실손의료비특약 (비급여 실손의료비)(갱신형) - 3대비급여형 비위험");
    codeToName.put("79578", "(무)실손의료비특약 (비급여 실손의료비)(갱신형) - 3대비급여형 비위험 갱신");
    codeToName.put("79579", "(무)실손의료비특약 (비급여 실손의료비)(갱신형) - 3대비급여형 중위험");
    codeToName.put("79580", "(무)실손의료비특약 (비급여 실손의료비)(갱신형) - 3대비급여형 중위험 갱신");
    codeToName.put("79581", "(무)실손의료비특약 (비급여 실손의료비)(갱신형) - 3대비급여형 고위험");
    codeToName.put("79582", "(무)실손의료비특약 (비급여 실손의료비)(갱신형) - 3대비급여형 고위험 갱신");
    codeToName.put("79583", "(무)실손의료비특약 (비급여 실손의료비)(갱신형) - 질병비급여형 태아계약");
    codeToName.put("79584", "(무)실손의료비특약 (비급여 실손의료비)(갱신형) - 상해비급여형 태아계약");
    codeToName.put("79585", "(무)실손의료비특약 (비급여 실손의료비)(갱신형) - 3대비급여형 태아계약");
    
    // 실손상품 주계약 코드들
    codeToName.put("21690", "(무)실손의료비보험 (종합형, 비위험, 최초계약)");
    codeToName.put("21691", "(무)실손의료비보험 (종합형, 비위험, 갱신계약)");
    codeToName.put("21692", "(무)실손의료비보험 (종합형, 중위험, 최초계약)");
    codeToName.put("21693", "(무)실손의료비보험 (종합형, 중위험, 갱신계약)");
    codeToName.put("21694", "(무)실손의료비보험 (종합형, 고위험, 최초계약)");
    codeToName.put("21695", "(무)실손의료비보험 (종합형, 고위험, 갱신계약)");
    codeToName.put("21696", "(무)실손의료비보험 (질병형, 최초계약)");
    codeToName.put("21697", "(무)실손의료비보험 (질병형, 갱신계약)");
    codeToName.put("21698", "(무)실손의료비보험 (상해형, 비위험, 최초계약)");
    codeToName.put("21699", "(무)실손의료비보험 (상해형, 비위험, 갱신계약)");
    codeToName.put("21700", "(무)실손의료비보험 (상해형, 중위험, 최초계약)");
    codeToName.put("21701", "(무)실손의료비보험 (상해형, 중위험, 갱신계약)");
    codeToName.put("21702", "(무)실손의료비보험 (상해형, 고위험, 최초계약)");
    codeToName.put("21703", "(무)실손의료비보험 (상해형, 고위험, 갱신계약)");
    codeToName.put("21712", "(무)실손의료비보험 (태아계약)");
    
    // 기존 다사랑암보험 코드들
    codeToName.put("79525", "(무)다(多)사랑암진단특약");
    codeToName.put("79527", "(무)다(多)사랑소액암New보장특약");
    codeToName.put("79957", "(무)전이암진단특약");
    codeToName.put("81880", "(무)전이암진단생활비특약");
    codeToName.put("81817", "(무)매년계속받는암진단특약");
    codeToName.put("81819", "(무)원투쓰리암진단특약");
    codeToName.put("83180", "(무)암(갑상선암및기타피부암제외)주요치료특약Ⅱ(연간1회한)");
    codeToName.put("83192", "(무)원투쓰리암진단특약(갱신형, 10년)");
    codeToName.put("81795", "(무)항암약물방사선치료(소액암)특약");
    codeToName.put("81797", "(무)통합항암약물방사선치료(전이암포함,1종)특약");
    codeToName.put("81799", "(무)통합항암약물방사선치료(전이암포함,2종)특약");
    codeToName.put("81801", "(무)통합항암약물방사선치료(전이암포함,3종)특약");
    codeToName.put("81803", "(무)통합항암약물방사선치료(전이암포함,4종)특약");
    codeToName.put("81805", "(무)통합항암약물방사선치료(전이암포함,5종)특약");
    codeToName.put("81807", "(무)통합항암약물방사선치료(전이암포함,6종)특약");
    codeToName.put("81809", "(무)통합항암약물방사선치료(전이암포함,7종)특약");
    codeToName.put("81811", "(무)통합항암약물방사선치료(전이암포함,8종)특약");
    codeToName.put("81813", "(무)통합항암약물방사선치료(전이암포함,9종)특약");
    codeToName.put("81815", "(무)통합항암약물방사선치료(전이암포함,10종)특약");
    codeToName.put("81825", "(무)항암약물치료특약Ⅴ");
    codeToName.put("81827", "(무)항암방사선치료특약Ⅴ");
    codeToName.put("81974", "(무)상급종합병원,국립암센터통합암주요치료(전이암포함,10종)특약");
    codeToName.put("82020", "(무)하이클래스암주요치료특약(연간1회한)");
    codeToName.put("82022", "(무)하이클래스항암약물치료특약(연간1회한)");
    codeToName.put("82057", "(무)항암중입자방사선치료특약");
    codeToName.put("83182", "(무)갑상선암및기타피부암주요치료특약Ⅱ(연간1회한)");
    codeToName.put("83184", "(무)상급종합병원, 국립암센터암(갑상선암및기타피부암제외)주요치료특약Ⅱ(연간1회한)");
    codeToName.put("83186", "(무)상급종합병원, 국립암센터갑상선암및기타피부암주요치료특약특약Ⅱ(연간1회한)");
    
    return codeToName.getOrDefault(code, "상품명 없음");
  }

  /** ⑦ 디버그용 쿼리 */
  public Map<String, Object> debugQuery(String insuCd, Integer age, Integer insuTerm, Integer payTerm) {
    Map<String, Object> result = new LinkedHashMap<>();
    
    try {
      // 디버그 데이터 조회
      Map<String, Object> debugData = insuMapper.selectPremRateForDebug(insuCd, age, insuTerm, payTerm);
      result.put("insuCd", insuCd);
      result.put("age", age);
      result.put("insuTerm", insuTerm);
      result.put("payTerm", payTerm);
      result.put("data", debugData);
    } catch (Exception e) {
      result.put("error", "디버그 쿼리 실패: " + e.getMessage());
    }
    
    return result;
  }

  /** UW_CODE_MAPPING 기반 파싱으로 보험기간, 납입기간, 가입나이 정보 추출 */
  private Map<String, String> parseTermsWithPython(File pdfFile, String insuCd) {
    try {
      // 1단계: UW_CODE_MAPPING 직접 조회 (최우선)
      log.info("=== UW_CODE_MAPPING 직접 조회 시작: {} ===", insuCd);
      Map<String, String> uwMappingDirectResult = getUwMappingDataDirectly(insuCd);
      
      if (!isEmptyOrDefault(uwMappingDirectResult)) {
        log.info("UW_CODE_MAPPING 직접 조회 성공: {}", insuCd);
        return uwMappingDirectResult;
      }
      
      // 2단계: UW_CODE_MAPPING 기반 검증 파싱
      log.info("=== UW_CODE_MAPPING 검증 파싱 시작: {} ===", insuCd);
      Map<String, String> uwMappingResult = uwMappingHybridParsingService.parseWithUwMappingValidation(pdfFile, insuCd);
      
      // UW_CODE_MAPPING 검증 결과가 유효한지 확인
      if (!isEmptyOrDefault(uwMappingResult) && 
          !"EMPTY".equals(uwMappingResult.get("validationSource"))) {
        log.info("UW_CODE_MAPPING 검증 파싱 성공: {} (신뢰도: {}%)", 
                 insuCd, uwMappingResult.get("validationConfidence"));
        return uwMappingResult;
      }
      
      // 3단계: 특수 조건을 가진 특약들은 하드코딩 사용
      if (isSpecialConditionRider(insuCd)) {
        log.info("=== 특수 조건 특약 {} 하드코딩 사용 ===", insuCd);
        Map<String, String> hardcodedTerms = getSpecialRiderTerms(insuCd);
        if (!isEmptyOrDefault(hardcodedTerms)) {
          log.info("특수 조건 특약 {} 하드코딩 적용 완료", insuCd);
          return hardcodedTerms;
        }
      }
      
      // 4단계: 기존 하이브리드 파싱 사용
      log.info("=== 기존 하이브리드 파싱 시작: {} ===", insuCd);
      Map<String, String> hybridResult = hybridParsingService.parseWithMultipleStrategies(pdfFile, insuCd);
      
      if (!isEmptyOrDefault(hybridResult)) {
        log.info("하이브리드 파싱 성공 - insuTerm: {}, payTerm: {}, ageRange: {}", 
                 hybridResult.get("insuTerm"), hybridResult.get("payTerm"), hybridResult.get("ageRange"));
        return hybridResult;
      }
      
      // 5단계: 사업방법서 기반 파싱 재시도
      log.warn("하이브리드 파싱 결과가 비어있음, 사업방법서 재시도: {}", insuCd);
      Map<String, String> businessMethodResult = getBusinessMethodTerms(pdfFile, insuCd);
      if (!isEmptyOrDefault(businessMethodResult)) {
        log.info("사업방법서 파싱 성공: {}", insuCd);
        return businessMethodResult;
      }
      
      // 최후의 수단으로만 기본값 사용
      log.error("모든 파싱 방법 실패, 기본값 사용: {}", insuCd);
      return getDefaultTerms(insuCd);
      
    } catch (Exception e) {
      log.error("UW_CODE_MAPPING 기반 파싱 오류: {}", e.getMessage(), e);
      
      // 오류 시 특수 조건 특약 하드코딩 시도
      if (isSpecialConditionRider(insuCd)) {
        log.info("오류 시 특수 조건 특약 {} 하드코딩 사용", insuCd);
        return getSpecialRiderTerms(insuCd);
      }
      
      return getDefaultTerms(insuCd);
    }
  }
  
  /** 특수 조건을 가진 특약인지 확인 */
  private boolean isSpecialConditionRider(String insuCd) {
    return Arrays.asList("81819", "81880", "83192").contains(insuCd);
  }
  
  /** PDF 파싱 결과가 비어있거나 기본값인지 확인 */
  private boolean isEmptyOrDefault(Map<String, String> terms) {
    return terms.get("insuTerm").equals("—") || terms.get("payTerm").equals("—") || 
           terms.get("ageRange").equals("—") || terms.get("insuTerm").isEmpty() || 
           terms.get("payTerm").isEmpty() || terms.get("ageRange").isEmpty();
  }
  
  /** UW_CODE_MAPPING 데이터 직접 조회 */
  private Map<String, String> getUwMappingDataDirectly(String insuCd) {
    try {
      List<UwCodeMappingData> mappingData = uwMappingValidationService.getValidationDataByCode(insuCd);
      
      if (mappingData.isEmpty()) {
        log.warn("UW_CODE_MAPPING에 데이터 없음: {}", insuCd);
        return getDefaultTerms(insuCd);
      }
      
      log.info("UW_CODE_MAPPING 데이터 직접 사용: {} ({} 건)", insuCd, mappingData.size());
      return convertUwMappingToParsedResult(mappingData);
      
    } catch (Exception e) {
      log.error("UW_CODE_MAPPING 직접 조회 오류: {}", e.getMessage(), e);
      return getDefaultTerms(insuCd);
    }
  }
  
  /** UW_CODE_MAPPING 데이터를 파싱 결과 형태로 변환 */
  private Map<String, String> convertUwMappingToParsedResult(List<UwCodeMappingData> mappingData) {
    Map<String, String> result = new LinkedHashMap<>();
    
    // 보험기간 집합
    Set<String> insuTerms = mappingData.stream()
        .map(UwCodeMappingData::getPeriodLabel)
        .collect(Collectors.toSet());
    
    // 납입기간 집합
    Set<String> payTerms = mappingData.stream()
        .map(UwCodeMappingData::getPayTerm)
        .collect(Collectors.toSet());
    
    result.put("insuTerm", String.join(", ", insuTerms));
    result.put("payTerm", String.join(", ", payTerms));
    result.put("ageRange", buildDetailedAgeRange(mappingData));
    result.put("renew", determineRenewType(mappingData.get(0).getCode()));
    result.put("specialNotes", "UW_CODE_MAPPING 기반 정확한 데이터");
    result.put("validationSource", "UW_CODE_MAPPING_DIRECT");
    
    return result;
  }
  
  /** 상세 가입나이 문자열 생성 - 개선된 버전 */
  private String buildDetailedAgeRange(List<UwCodeMappingData> mappingData) {
    if (mappingData.isEmpty()) {
      return "—";
    }
    
    // 첫 번째 레코드의 가입나이 데이터 확인
    UwCodeMappingData firstRecord = mappingData.get(0);
    String entryAgeM = firstRecord.getEntryAgeM();
    // String entryAgeF = firstRecord.getEntryAgeF(); // 사용되지 않음
    
    // 이미 통합된 형태인지 확인 (쉼표와 "년납" 패턴이 있으면 통합된 형태)
    if (entryAgeM != null && entryAgeM.contains(",") && entryAgeM.contains("년납")) {
      // 통합된 형태라면 그대로 사용
      log.debug("통합된 가입나이 형태 사용: {}", entryAgeM);
      return entryAgeM;
    }
    
    // 분리된 형태라면 파이프로 연결하여 통합
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
      
      // 납입기간별 가입나이 추가 (파이프 연결 방식)
      for (int i = 0; i < periodData.size(); i++) {
        UwCodeMappingData data = periodData.get(i);
        
        if (i > 0) {
          ageRangeBuilder.append(", ");
        }
        
        // 납입기간 정규화
        String normalizedPayTerm = normalizePayTerm(data.getPayTerm());
        
        // 남성/여성 가입나이를 파이프로 연결
        String combinedAge = combineAgeRanges(data.getEntryAgeM(), data.getEntryAgeF());
        
        ageRangeBuilder.append(String.format("%s(%s)",
            normalizedPayTerm,
            combinedAge));
      }
    }
    
    return ageRangeBuilder.toString();
  }
  
  /** 남성/여성 가입나이를 파이프로 연결하는 헬퍼 메서드 */
  private String combineAgeRanges(String maleAge, String femaleAge) {
    if (maleAge == null && femaleAge == null) {
      return "—";
    }
    
    if (maleAge == null) {
      return "여:" + femaleAge;
    }
    
    if (femaleAge == null) {
      return "남:" + maleAge;
    }
    
    // 남성과 여성 나이가 동일한 경우
    if (maleAge.equals(femaleAge)) {
      return "남:" + maleAge + ", 여:" + femaleAge;
    }
    
    // 남성과 여성 나이가 다른 경우
    return "남:" + maleAge + ", 여:" + femaleAge;
  }
  
  /** 납입기간 정규화 헬퍼 메서드 */
  private String normalizePayTerm(String payTerm) {
    if (payTerm == null || payTerm.trim().isEmpty()) {
      return "—";
    }
    
    String trimmed = payTerm.trim();
    
    // "월납(10년납)" -> "10년납"
    if (trimmed.contains("월납(") && trimmed.contains(")")) {
      String extracted = trimmed.substring(trimmed.indexOf("(") + 1, trimmed.lastIndexOf(")"));
      log.debug("납입기간 정규화: '{}' -> '{}'", trimmed, extracted);
      return extracted;
    }
    
    // "월납(전기납)" -> "전기납"
    if (trimmed.contains("월납(") && trimmed.contains("전기납")) {
      return "전기납";
    }
    
    // 이미 정규화된 형태라면 그대로 반환
    if (trimmed.matches("\\d+년납|전기납|일시납")) {
      return trimmed;
    }
    
    // 기본값 반환
    return trimmed;
  }
  
  /** 갱신여부 판단 */
  private String determineRenewType(String insuCd) {
    // 특약별 갱신여부 판단 로직
    if (insuCd.startsWith("8")) {
      return "갱신형"; // 8로 시작하는 특약들은 대부분 갱신형
    }
    return "비갱신형";
  }

  /** 기본값 반환 (모든 파싱 전략 실패 시) */
  private Map<String, String> getDefaultTerms(String insuCd) {
    Map<String, String> defaultTerms = new LinkedHashMap<>();
    defaultTerms.put("insuTerm", "—");
    defaultTerms.put("payTerm", "—");
    defaultTerms.put("ageRange", "—");
    defaultTerms.put("renew", "—");
    defaultTerms.put("specialNotes", "모든 파싱 전략 실패");
    log.warn("모든 파싱 전략 실패, 기본값 사용: {}", insuCd);
    return defaultTerms;
  }
  
  /** 사업방법서 기반 조건 추출 (PDF 내용에서 상품명칭으로 조건 찾기) */
  private Map<String, String> getBusinessMethodTerms(File pdfFile, String insuCd) {
    try {
      // PDF에서 텍스트 추출
      String text = PdfParser.readAllText(pdfFile);
      
      // 상품명칭 찾기
      String productName = findProductNameByCode(text, insuCd);
      if (productName == null) {
        log.warn("상품명칭을 찾을 수 없음: {}", insuCd);
        return getDefaultTerms(insuCd);
      }
      
      log.info("상품명칭 발견: {} -> {}", insuCd, productName);
      
      // 사업방법서에서 해당 상품명칭의 조건 추출
      return extractTermsFromBusinessMethod(text, productName, insuCd);
      
    } catch (Exception e) {
      log.error("사업방법서 기반 조건 추출 오류: {}", e.getMessage(), e);
      return getDefaultTerms(insuCd);
    }
  }
  
  /** 상품코드로 상품명칭 찾기 */
  private String findProductNameByCode(String text, String insuCd) {
    try {
      // 상품코드 주변의 텍스트에서 상품명 추출
      String pattern = String.format("([^\\n]*?)\\s*%s\\s*([^\\n]*)", insuCd);
      java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
      java.util.regex.Matcher matcher = regex.matcher(text);
      
      if (matcher.find()) {
        String beforeCode = matcher.group(1).trim();
        String afterCode = matcher.group(2).trim();
        String fullName = (beforeCode + " " + afterCode).trim();
        
        // 의미있는 상품명인지 확인 (너무 짧거나 특수문자만 있으면 제외)
        if (fullName.length() > 5 && !fullName.matches("[^가-힣]*")) {
          return fullName;
        }
      }
      
      // 대안: 상품코드가 포함된 라인 전체에서 상품명 추출
      String[] lines = text.split("\\n");
      for (String line : lines) {
        if (line.contains(insuCd)) {
          // 상품코드 제거 후 나머지 텍스트를 상품명으로 사용
          String productName = line.replaceAll("\\s*" + insuCd + "\\s*", "").trim();
          if (productName.length() > 3 && productName.matches(".*[가-힣].*")) {
            return productName;
          }
        }
      }
      
    } catch (Exception e) {
      log.debug("상품명칭 찾기 실패: {}", e.getMessage());
    }
    
    return null;
  }
  
  /** 사업방법서에서 상품명칭으로 조건 추출 */
  private Map<String, String> extractTermsFromBusinessMethod(String text, String productName, String insuCd) {
    Map<String, String> terms = new LinkedHashMap<>();
    
    try {
      // "4. 사업방법" 섹션 찾기
      String businessMethodSection = extractBusinessMethodSection(text);
      if (businessMethodSection == null) {
        log.warn("사업방법 섹션을 찾을 수 없음");
        return getDefaultTerms(insuCd);
      }
      
      // 상품명칭이 사업방법서에 있는지 확인
      if (!businessMethodSection.contains(productName)) {
        log.warn("상품명칭이 사업방법서에 없음: {}", productName);
        return getDefaultTerms(insuCd);
      }
      
      // 상품명칭 주변에서 조건 추출
      terms = extractTermsAroundProductName(businessMethodSection, productName);
      
      // 추출된 조건이 유효한지 확인
      if (terms.get("insuTerm").equals("—") && terms.get("payTerm").equals("—")) {
        log.warn("상품명칭 주변에서 조건을 추출할 수 없음: {}", productName);
        return getDefaultTerms(insuCd);
      }
      
      terms.put("specialNotes", "사업방법서 기반 추출: " + productName);
      
    } catch (Exception e) {
      log.error("사업방법서 조건 추출 오류: {}", e.getMessage(), e);
    }
    
    return terms;
  }
  
  /** 사업방법 섹션 추출 */
  private String extractBusinessMethodSection(String text) {
    try {
      // "4. 사업방법" 섹션 추출
      String[] patterns = {
        "4\\.\\s*사업방법(.*?)(?=5\\.|6\\.|7\\.|$)",
        "4\\.\\s*사업방법(.*?)(?=\\n\\s*[-\\*]\\s*\\(무\\)|$)",
        "4\\.\\s*사업방법(.*?)(?=\\n주\\)|$)"
      };
      
      for (String pattern : patterns) {
        java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = regex.matcher(text);
        
        if (matcher.find()) {
          String section = matcher.group(1).trim();
          if (section.length() > 100) { // 최소 길이 체크
            return section;
          }
        }
      }
      
    } catch (Exception e) {
      log.debug("사업방법 섹션 추출 실패: {}", e.getMessage());
    }
    
    return null;
  }
  
  /** 상품명칭 주변에서 조건 추출 */
  private Map<String, String> extractTermsAroundProductName(String section, String productName) {
    Map<String, String> terms = new LinkedHashMap<>();
    terms.put("insuTerm", "—");
    terms.put("payTerm", "—");
    terms.put("ageRange", "—");
    terms.put("renew", "—");
    
    try {
      // 상품명칭이 포함된 라인 찾기
      String[] lines = section.split("\\n");
      int productLineIndex = -1;
      
      for (int i = 0; i < lines.length; i++) {
        if (lines[i].contains(productName)) {
          productLineIndex = i;
          break;
        }
      }
      
      if (productLineIndex == -1) {
        return terms;
      }
      
      // 상품명칭 주변 라인들에서 조건 추출
      for (int i = Math.max(0, productLineIndex - 3); i <= Math.min(lines.length - 1, productLineIndex + 3); i++) {
        String line = lines[i].trim();
        
        // 보험기간 추출
        if (terms.get("insuTerm").equals("—")) {
          String insuTerm = extractInsuranceTerm(line);
          if (!insuTerm.equals("—")) {
            terms.put("insuTerm", insuTerm);
          }
        }
        
        // 납입기간 추출
        if (terms.get("payTerm").equals("—")) {
          String payTerm = extractPaymentTerm(line);
          if (!payTerm.equals("—")) {
            terms.put("payTerm", payTerm);
          }
        }
        
        // 가입나이 추출
        if (terms.get("ageRange").equals("—")) {
          String ageRange = extractAgeRange(line);
          if (!ageRange.equals("—")) {
            terms.put("ageRange", ageRange);
          }
        }
        
        // 갱신여부 추출
        if (terms.get("renew").equals("—")) {
          if (line.contains("갱신형")) {
            terms.put("renew", "갱신형");
          } else if (line.contains("비갱신")) {
            terms.put("renew", "비갱신형");
          }
        }
      }
      
    } catch (Exception e) {
      log.debug("상품명칭 주변 조건 추출 실패: {}", e.getMessage());
    }
    
    return terms;
  }
  
  /** 보험기간 추출 */
  private String extractInsuranceTerm(String line) {
    String[] patterns = {
      "종신", "평생", "\\d+세\\s*까지", "\\d+세\\s*종료", "\\d+년만기"
    };
    
    for (String pattern : patterns) {
      if (line.matches(".*" + pattern + ".*")) {
        return line.replaceAll(".*?(" + pattern + ").*", "$1").trim();
      }
    }
    
    return "—";
  }
  
  /** 납입기간 추출 */
  private String extractPaymentTerm(String line) {
    String[] patterns = {
      "전기납", "일시납", "\\d+년납", "월납"
    };
    
    for (String pattern : patterns) {
      if (line.matches(".*" + pattern + ".*")) {
        return line.replaceAll(".*?(" + pattern + ").*", "$1").trim();
      }
    }
    
    return "—";
  }
  
  /** 가입나이 추출 */
  private String extractAgeRange(String line) {
    String[] patterns = {
      "만?\\s*\\d+세\\s*~\\s*\\d+세",
      "\\d+세\\s*~\\s*\\d+세"
    };
    
    for (String pattern : patterns) {
      java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
      java.util.regex.Matcher matcher = regex.matcher(line);
      
      if (matcher.find()) {
        return matcher.group().trim();
      }
    }
    
    return "—";
  }

  /** Python을 사용한 PDF 파싱으로 보험기간, 납입기간, 가입나이 정보 추출 */
  public Map<String, Object> parsePdfWithPython(String insuCd) {
    try {
      // PDF 파일 찾기
      File pdfFile = PdfParser.findPdfForCode(pdfDir, insuCd);
      if (pdfFile == null) {
        return Map.of("error", "PDF 파일을 찾을 수 없습니다: " + insuCd);
      }
      
      // Python 스크립트로 파싱
      Map<String, Object> result = pythonPdfService.extractProductInfo(pdfFile.getAbsolutePath(), insuCd);
      
      if (result.containsKey("error")) {
        return result;
      }
      
      // 결과를 표준 형식으로 변환
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("insuCd", insuCd);
      response.put("success", true);
      
      if (result.containsKey("terms")) {
        @SuppressWarnings("unchecked")
        Map<String, Object> terms = (Map<String, Object>) result.get("terms");
        response.put("insuTerm", terms.get("insuTerm"));
        response.put("payTerm", terms.get("payTerm"));
        response.put("ageRange", terms.get("ageRange"));
        response.put("renew", terms.get("renew"));
        response.put("specialNotes", terms.get("specialNotes"));
      }
      
      if (result.containsKey("limits")) {
        @SuppressWarnings("unchecked")
        Map<String, Object> limits = (Map<String, Object>) result.get("limits");
        response.put("minWon", limits.get("minWon"));
        response.put("maxWon", limits.get("maxWon"));
        response.put("matchedLine", limits.get("matchedLine"));
      }
      
      return response;
      
    } catch (Exception e) {
      log.error("Python PDF 파싱 오류: {}", e.getMessage(), e);
      return Map.of("error", "Python PDF 파싱 오류: " + e.getMessage());
    }
  }

  /** 하이브리드 PDF 파싱: Java + Python 조합 */
  public Map<String, Object> parsePdfHybrid(String insuCd) {
    try {
      // PDF 파일 찾기
      File pdfFile = PdfParser.findPdfForCode(pdfDir, insuCd);
      if (pdfFile == null) {
        return Map.of("error", "PDF 파일을 찾을 수 없습니다: " + insuCd);
      }
      
      // 1단계: Java PDFBox로 파싱 시도
      Map<String, Object> javaResult = parsePdfWithJava(pdfFile, insuCd);
      
      // 2단계: Python OCR로 파싱 시도
      Map<String, Object> pythonResult = parsePdfWithPython(insuCd);
      
      // 3단계: 두 결과를 결합하여 더 정확한 결과 생성
      return combineParsingResults(javaResult, pythonResult, insuCd);
      
    } catch (Exception e) {
      log.error("하이브리드 PDF 파싱 오류: {}", e.getMessage(), e);
      return Map.of("error", "하이브리드 PDF 파싱 오류: " + e.getMessage());
    }
  }
  
  /** Java PDFBox로 파싱 */
  private Map<String, Object> parsePdfWithJava(File pdfFile, String insuCd) {
    try {
      String text = PdfParser.readAllText(pdfFile);
      Sections sections = PdfParser.splitSections(text);
      Map<String, String> terms = PdfParser.parseTerms(sections.block4);
      
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("insuCd", insuCd);
      result.put("method", "java");
      result.put("terms", terms);
      result.put("success", true);
      
      return result;
    } catch (Exception e) {
      log.error("Java PDFBox 파싱 오류: {}", e.getMessage(), e);
      return Map.of("error", "Java PDFBox 파싱 오류: " + e.getMessage(), "success", false);
    }
  }
  
  /** 파싱 결과 결합 */
  private Map<String, Object> combineParsingResults(Map<String, Object> javaResult, 
                                                   Map<String, Object> pythonResult, 
                                                   String insuCd) {
    Map<String, Object> combined = new LinkedHashMap<>();
    combined.put("insuCd", insuCd);
    combined.put("success", true);
    combined.put("method", "hybrid");
    
    // Java 결과가 성공적이면 Java 우선, 아니면 Python 결과 사용
    if (javaResult.containsKey("success") && (Boolean) javaResult.get("success")) {
      combined.put("primarySource", "java");
      combined.put("terms", javaResult.get("terms"));
    } else if (pythonResult.containsKey("success") && (Boolean) pythonResult.get("success")) {
      combined.put("primarySource", "python");
      combined.put("terms", pythonResult.get("terms"));
    } else {
      // 둘 다 실패한 경우
      combined.put("success", false);
      combined.put("error", "Java와 Python 파싱 모두 실패");
    }
    
    return combined;
  }

  /** 보험기간과 납입기간의 모든 조합을 생성하는 메서드 */
  private List<PolicyTerms> generateTermCombinations(Map<String, String> termsMap) {
    List<PolicyTerms> combinations = new ArrayList<>();
    
    String insuTermStr = termsMap.get("insuTerm");
    String payTermStr = termsMap.get("payTerm");
    String ageRange = termsMap.get("ageRange");
    String renew = termsMap.get("renew");
    String specialNotes = termsMap.get("specialNotes");
    
    // 보험기간 파싱 (쉼표로 구분된 값들을 리스트로 변환)
    List<String> insuTerms = parseCommaSeparatedValues(insuTermStr);
    if (insuTerms.isEmpty()) {
      insuTerms.add("—");
    }
    
    // 납입기간 파싱 (쉼표로 구분된 값들을 리스트로 변환)
    List<String> payTerms = parseCommaSeparatedValues(payTermStr);
    if (payTerms.isEmpty()) {
      payTerms.add("—");
    }
    
    log.info("[generateTermCombinations] insuTerms: {}, payTerms: {}", insuTerms, payTerms);
    
    // 모든 조합 생성
    for (String insuTerm : insuTerms) {
      for (String payTerm : payTerms) {
        // 각 납입기간에 맞는 나이 범위 추출
        String specificAgeRange = extractAgeRangeForPayTerm(ageRange, payTerm.trim());
        
        PolicyTerms combination = PolicyTerms.builder()
            .insuTerm(insuTerm.trim())
            .payTerm(payTerm.trim())
            .ageRange(specificAgeRange)
            .renew(renew)
            .specialNotes(specialNotes)
            .build();
        
        combinations.add(combination);
        log.debug("[generateTermCombinations] 생성된 조합: insuTerm={}, payTerm={}, ageRange={}", 
                 insuTerm.trim(), payTerm.trim(), specificAgeRange);
      }
    }
    
    log.info("[generateTermCombinations] 총 {} 개의 조합 생성", combinations.size());
    return combinations;
  }
  
  /** 쉼표로 구분된 문자열을 리스트로 파싱하는 헬퍼 메서드 */
  private List<String> parseCommaSeparatedValues(String input) {
    List<String> result = new ArrayList<>();
    
    if (input == null || input.trim().isEmpty() || "—".equals(input.trim())) {
      return result;
    }
    
    // 쉼표로 분리하고 각 값을 trim
    String[] parts = input.split(",");
    for (String part : parts) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    
    return result;
  }

  /** ⑧ 기준금액 기반 보험료 계산 */
  public Map<String, Object> calculatePremiumByAmount(String insuCd, Integer age, Integer insuTerm, Integer payTerm, BigDecimal baseAmount) {
    Map<String, Object> result = new LinkedHashMap<>();
    List<String> errors = new ArrayList<>();
    
    try {
      // 기본값 설정
      int targetAge = age != null ? age : 15;
      int targetInsuTerm = insuTerm != null ? insuTerm : 10;
      int targetPayTerm = payTerm != null ? payTerm : 10;
      BigDecimal amount = baseAmount != null ? baseAmount : BigDecimal.valueOf(100);
      
      // 보험료 데이터 조회
      PremRateRow premRate = insuMapper.selectPremRate(insuCd, targetAge, targetInsuTerm, targetPayTerm);
      
      if (premRate == null) {
        errors.add("보험료 데이터가 없습니다");
        result.put("errors", errors);
        return result;
      }
      
      // 보험료 계산: (기준금액 * 요율 / 기준구성금액) * 10000
      BigDecimal stndAmt = nz(premRate.getStndAmt());
      BigDecimal manRate = nz(premRate.getManRate());
      BigDecimal fmlRate = nz(premRate.getFmlRate());
      
      if (stndAmt.compareTo(BigDecimal.ZERO) == 0) {
        errors.add("기준구성금액이 0입니다");
        result.put("errors", errors);
        return result;
      }
      
      // 남성 보험료 계산: (기준금액 * 요율 * 10000) / 기준구성금액
      BigDecimal manPremium = amount.multiply(manRate).multiply(BigDecimal.valueOf(10000)).divide(stndAmt, 0, RoundingMode.HALF_UP);
      
      // 여성 보험료 계산: (기준금액 * 요율 * 10000) / 기준구성금액
      BigDecimal fmlPremium = amount.multiply(fmlRate).multiply(BigDecimal.valueOf(10000)).divide(stndAmt, 0, RoundingMode.HALF_UP);
      
      result.put("insuCd", insuCd);
      result.put("age", targetAge);
      result.put("insuTerm", targetInsuTerm);
      result.put("payTerm", targetPayTerm);
      result.put("baseAmount", amount);
      result.put("manPremium", manPremium);
      result.put("fmlPremium", fmlPremium);
      result.put("stndAmt", stndAmt);
      result.put("manRate", manRate);
      result.put("fmlRate", fmlRate);
      
    } catch (Exception e) {
      errors.add("보험료 계산 오류: " + e.getMessage());
    }
    
    result.put("errors", errors);
    return result;
  }

  /** ⑨ 조합별 보험료 계산 - 보험기간과 납입기간 문자열을 숫자로 변환하여 계산 */
  public Map<String, Object> calculatePremiumByTerms(String insuCd, Integer age, String insuTermStr, String payTermStr, BigDecimal baseAmount) {
    Map<String, Object> result = new LinkedHashMap<>();
    List<String> errors = new ArrayList<>();
    
    try {
      // 기본값 설정
      int targetAge = age != null ? age : 15;
      BigDecimal amount = baseAmount != null ? baseAmount : BigDecimal.valueOf(100);
      
      // 보험기간과 납입기간을 숫자로 변환
      Integer insuTermNum = parseTermToNumber(insuTermStr);
      Integer payTermNum = parseTermToNumber(payTermStr);
      
      if (insuTermNum == null) {
        errors.add("보험기간을 숫자로 변환할 수 없습니다: " + insuTermStr);
        result.put("errors", errors);
        return result;
      }
      
      if (payTermNum == null) {
        errors.add("납입기간을 숫자로 변환할 수 없습니다: " + payTermStr);
        result.put("errors", errors);
        return result;
      }
      
      // 보험료 데이터 조회
      PremRateRow premRate = insuMapper.selectPremRate(insuCd, targetAge, insuTermNum, payTermNum);
      
      if (premRate == null) {
        errors.add(String.format("%s세, 보험기간 %d, 납입기간 %d 최소 or 최대 데이터 없음", targetAge, insuTermNum, payTermNum));
        result.put("errors", errors);
        return result;
      }
      
      // 보험료 계산: (기준금액 * 요율 / 기준구성금액) * 10000
      BigDecimal stndAmt = nz(premRate.getStndAmt());
      BigDecimal manRate = nz(premRate.getManRate());
      BigDecimal fmlRate = nz(premRate.getFmlRate());
      
      if (stndAmt.compareTo(BigDecimal.ZERO) == 0) {
        errors.add("기준구성금액이 0입니다");
        result.put("errors", errors);
        return result;
      }
      
      // 남성 보험료 계산: (기준금액 * 요율 * 10000) / 기준구성금액
      BigDecimal manPremium = amount.multiply(manRate).multiply(BigDecimal.valueOf(10000)).divide(stndAmt, 0, RoundingMode.HALF_UP);
      
      // 여성 보험료 계산: (기준금액 * 요율 * 10000) / 기준구성금액
      BigDecimal fmlPremium = amount.multiply(fmlRate).multiply(BigDecimal.valueOf(10000)).divide(stndAmt, 0, RoundingMode.HALF_UP);
      
      result.put("insuCd", insuCd);
      result.put("age", targetAge);
      result.put("insuTerm", insuTermStr);
      result.put("payTerm", payTermStr);
      result.put("insuTermNum", insuTermNum);
      result.put("payTermNum", payTermNum);
      result.put("baseAmount", amount);
      result.put("manPremium", manPremium);
      result.put("fmlPremium", fmlPremium);
      result.put("stndAmt", stndAmt);
      result.put("manRate", manRate);
      result.put("fmlRate", fmlRate);
      
    } catch (Exception e) {
      errors.add("보험료 계산 오류: " + e.getMessage());
    }
    
    result.put("errors", errors);
    return result;
  }

  /** 특정 납입기간에 맞는 나이 범위를 추출하는 메서드 (개선된 버전) */
  private String extractAgeRangeForPayTerm(String fullAgeRange, String payTerm) {
    if (fullAgeRange == null || fullAgeRange.trim().isEmpty()) {
      return "—";
    }
    
    // 납입기간이 비어있거나 "—"인 경우 전체 나이 범위 반환
    if (payTerm == null || payTerm.trim().isEmpty() || "—".equals(payTerm.trim())) {
      return fullAgeRange;
    }
    
    // 납입기간 정규화 적용
    String normalizedPayTerm = normalizePayTerm(payTerm);
    String trimmedAgeRange = fullAgeRange.trim();
    
    log.debug("가입나이 추출: 정규화된 납입기간='{}', 원본 납입기간='{}'", normalizedPayTerm, payTerm);
    
    // 특별한 경우들 처리
    if ("전기납".equals(normalizedPayTerm)) {
      // 전기납인 경우 특별 처리
      if (trimmedAgeRange.contains("전기납")) {
        return extractAgeRangeForSpecialTerm(trimmedAgeRange, "전기납");
      }
    }
    
    // 보험기간별 처리 (90세만기, 100세만기 등)
    if (trimmedAgeRange.contains("90세만기:") || trimmedAgeRange.contains("100세만기:")) {
      return extractAgeRangeForInsuranceTerm(trimmedAgeRange, normalizedPayTerm);
    }
    
    // 납입기간 숫자 추출 (예: "10년납" -> "10")
    String payTermNumber = normalizedPayTerm.replaceAll("[^0-9]", "");
    if (payTermNumber.isEmpty()) {
      log.debug("납입기간에서 숫자를 추출할 수 없음: {}", normalizedPayTerm);
      return fullAgeRange; // 숫자를 추출할 수 없으면 전체 반환
    }
    
    // 나이 범위 문자열에서 해당 납입기간에 맞는 부분 찾기
    // 예: "10년납(남:만15세~80세, 여:만15세~80세)" 패턴 매칭
    String pattern = payTermNumber + "년납\\([^)]+\\)";
    
    try {
      java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
      java.util.regex.Matcher matcher = regex.matcher(trimmedAgeRange);
      
      if (matcher.find()) {
        String matchedAgeRange = matcher.group();
        // 괄호 안의 내용만 추출하여 더 간결하게 표시
        String ageContent = matchedAgeRange.substring(matchedAgeRange.indexOf("(") + 1, matchedAgeRange.lastIndexOf(")"));
        
        log.debug("납입기간 {}에 대한 가입나이 추출 성공: {}", normalizedPayTerm, ageContent);
        
        // 남성/여성 나이 범위를 더 간결하게 표시
        if (ageContent.contains("남:") && ageContent.contains("여:")) {
          String maleAge = extractAgeFromGender(ageContent, "남:");
          String femaleAge = extractAgeFromGender(ageContent, "여:");
          
          if (maleAge.equals(femaleAge)) {
            return String.format("남: %s, 여: %s", maleAge, femaleAge);
          } else {
            return String.format("남: %s, 여: %s", maleAge, femaleAge);
          }
        }
        
        return ageContent;
      } else {
        log.debug("납입기간 {}에 대한 가입나이 패턴 매칭 실패", normalizedPayTerm);
      }
    } catch (Exception e) {
      log.debug("나이 범위 추출 실패: payTerm={}, normalizedPayTerm={}, ageRange={}, error={}", 
               payTerm, normalizedPayTerm, fullAgeRange, e.getMessage());
    }
    
    // 매칭되지 않으면 전체 나이 범위 반환
    log.debug("매칭되지 않아 전체 나이 범위 반환: {}", fullAgeRange);
    return fullAgeRange;
  }
  
  /** 보험기간별 나이 범위 추출 (90세만기, 100세만기) */
  private String extractAgeRangeForInsuranceTerm(String ageRange, String payTerm) {
    try {
      // 90세만기와 100세만기 섹션 분리
      String[] sections = ageRange.split(";");
      
      for (String section : sections) {
        if (section.contains(payTerm)) {
          // 해당 납입기간이 포함된 섹션에서 나이 범위 추출
          String pattern = payTerm + "\\(남:[^,)]+,\\s*여:[^)]+\\)";
          java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
          java.util.regex.Matcher matcher = regex.matcher(section);
          
          if (matcher.find()) {
            String matched = matcher.group();
            String ageContent = matched.substring(matched.indexOf("(") + 1, matched.lastIndexOf(")"));
            
            if (ageContent.contains("남:") && ageContent.contains("여:")) {
              String maleAge = extractAgeFromGender(ageContent, "남:");
              String femaleAge = extractAgeFromGender(ageContent, "여:");
              return String.format("남: %s, 여: %s", maleAge, femaleAge);
            }
          }
        }
      }
    } catch (Exception e) {
      log.debug("보험기간별 나이 범위 추출 실패: {}", e.getMessage());
    }
    
    return ageRange;
  }
  
  /** 특별한 납입기간(전기납 등)에 대한 나이 범위 추출 */
  private String extractAgeRangeForSpecialTerm(String ageRange, String specialTerm) {
    try {
      if (ageRange.contains(specialTerm)) {
        String pattern = specialTerm + "\\([^)]+\\)";
        java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher matcher = regex.matcher(ageRange);
        
        if (matcher.find()) {
          String matched = matcher.group();
          String ageContent = matched.substring(matched.indexOf("(") + 1, matched.lastIndexOf(")"));
          
          if (ageContent.contains("남:") && ageContent.contains("여:")) {
            String maleAge = extractAgeFromGender(ageContent, "남:");
            String femaleAge = extractAgeFromGender(ageContent, "여:");
            return String.format("남: %s, 여: %s", maleAge, femaleAge);
          }
          
          return ageContent;
        }
      }
    } catch (Exception e) {
      log.debug("특별 납입기간 나이 범위 추출 실패: {}", e.getMessage());
    }
    
    return ageRange;
  }
  
  /** 성별 나이 범위 추출 헬퍼 메서드 */
  private String extractAgeFromGender(String ageContent, String gender) {
    int genderIndex = ageContent.indexOf(gender);
    if (genderIndex == -1) return "—";
    
    String afterGender = ageContent.substring(genderIndex + gender.length());
    int commaIndex = afterGender.indexOf(",");
    int endIndex = afterGender.indexOf(")");
    
    if (commaIndex != -1 && endIndex != -1) {
      endIndex = Math.min(commaIndex, endIndex);
    } else if (commaIndex != -1) {
      endIndex = commaIndex;
    } else if (endIndex == -1) {
      endIndex = afterGender.length();
    }
    
    String result = afterGender.substring(0, endIndex).trim();
    
    // "만"과 "세" 완전 제거하고 숫자만 남기기 (예: "만만15세~80세세" → "15~80")
    result = result.replaceAll("[만세]", ""); // "만"과 "세" 모두 제거
    result = result.trim();
    
    return result;
  }

  /** 특약 코드인지 판별하는 헬퍼 메서드 (현재 사용되지 않음) */
  @SuppressWarnings("unused")
  private boolean isSpecialRider(String insuCd) {
    if (insuCd == null || insuCd.length() != 5) {
      return false;
    }
    
    // 특약 코드 패턴: 7로 시작하는 5자리 숫자
    // 예: 79525, 79527, 79957, 81815 등
    return insuCd.startsWith("7") && insuCd.matches("\\d{5}");
  }
  
  /** 특약별 사업방법 조건을 반환하는 메서드 (선택적 하드코딩 + LLM 파싱) */
  private Map<String, String> getSpecialRiderTerms(String insuCd) {
    Map<String, String> terms = new LinkedHashMap<>();
    
    switch (insuCd) {
      // 특수 조건을 가진 특약들만 하드코딩 (사업방법서 기반)
      case "81819": // (무)원투쓰리암진단특약
        terms.put("insuTerm", "90세만기, 100세만기");
        terms.put("payTerm", "10년납, 15년납, 20년납, 30년납");
        terms.put("ageRange", "90세만기: 10년납(남:15~75,여:15~75), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~60,여:15~60); 100세만기: 10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)");
        terms.put("renew", "비갱신형");
        terms.put("specialNotes", "원투쓰리암진단특약 - 사업방법 기준");
        log.info("특약 {} 특수 조건 적용 (90세만기, 100세만기)", insuCd);
        break;
        
      case "81880": // (무)전이암진단생활비특약
        terms.put("insuTerm", "5년만기, 10년만기");
        terms.put("payTerm", "전기납");
        terms.put("ageRange", "5년만기: 최초(남:15~80,여:15~80), 갱신(남:20~99,여:20~99); 10년만기: 최초(남:15~80,여:15~80), 갱신(남:25~99,여:25~99)");
        terms.put("renew", "갱신형");
        terms.put("specialNotes", "전이암진단생활비특약 - 사업방법 기준");
        log.info("특약 {} 특수 조건 적용 (5년만기, 10년만기, 전기납)", insuCd);
        break;
        
      case "83192": // (무)원투쓰리암진단특약(갱신형, 10년)
        terms.put("insuTerm", "10년만기");
        terms.put("payTerm", "전기납");
        terms.put("ageRange", "최초(남:15~80,여:15~80), 갱신(남:25~99,여:25~99)");
        terms.put("renew", "갱신형");
        terms.put("specialNotes", "원투쓰리암진단특약 갱신형 - 사업방법 기준");
        log.info("특약 {} 특수 조건 적용 (10년만기, 전기납)", insuCd);
        break;
        
      // 나머지 특약들은 LLM 파싱 사용 (주계약과 동일한 조건)
      default:
        terms.put("insuTerm", "—");
        terms.put("payTerm", "—");
        terms.put("ageRange", "—");
        terms.put("renew", "—");
        terms.put("specialNotes", "LLM 파싱으로 사업방법서에서 추출 필요");
        log.info("특약 {} LLM 파싱 사용 (주계약과 동일한 조건)", insuCd);
        break;
    }
    
    return terms;
  }

  /** 주계약 조건을 상속하는 메서드 (호환성 유지 - 현재 사용되지 않음) */
  @SuppressWarnings("unused")
  private Map<String, String> inheritMainContractTerms() {
    return getSpecialRiderTerms("79525"); // 기본값으로 다사랑암진단특약 조건 사용
  }

  /** 보험기간/납입기간 문자열을 숫자로 변환하는 헬퍼 메서드 (개선된 버전) */
  private Integer parseTermToNumber(String termStr) {
    if (termStr == null || termStr.trim().isEmpty() || "—".equals(termStr.trim())) {
      return null;
    }
    
    // 납입기간 정규화 적용
    String normalized = normalizePayTerm(termStr);
    String trimmed = normalized.trim();
    
    log.debug("납입기간 숫자 변환: 원본='{}', 정규화='{}'", termStr, trimmed);
    
    // "종신" -> 999 (종신보험)
    if (trimmed.contains("종신")) {
      return 999;
    }
    
    // "90세만기" -> 90
    if (trimmed.contains("90세만기")) {
      return 90;
    }
    
    // "100세만기" -> 100
    if (trimmed.contains("100세만기")) {
      return 100;
    }
    
    // "전기납" -> 1 (전기납입)
    if (trimmed.contains("전기납")) {
      return 1;
    }
    
    // "월납" -> 0 (월납)
    if (trimmed.contains("월납")) {
      return 0;
    }
    
    // 숫자 추출 (예: "10년납" -> 10, "15년" -> 15)
    String numberStr = trimmed.replaceAll("[^0-9]", "");
    if (!numberStr.isEmpty()) {
      try {
        int result = Integer.parseInt(numberStr);
        log.debug("납입기간 숫자 변환 성공: '{}' -> {}", trimmed, result);
        return result;
      } catch (NumberFormatException e) {
        log.debug("숫자 변환 실패: {}", trimmed);
      }
    }
    
    // 변환 실패 시 경고 로그
    log.warn("납입기간을 숫자로 변환할 수 없습니다: 원본='{}', 정규화='{}'", termStr, trimmed);
    return null;
  }
}
