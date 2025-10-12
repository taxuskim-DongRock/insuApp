package com.example.insu.util;

import org.slf4j.Logger;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Slf4j
public class PdfParser {

  /** PDF 전체 텍스트 추출(PDFBox 2.x: PDDocument.load 사용) */
  public static String readAllText(File pdf) throws IOException {
    try (PDDocument doc = PDDocument.load(pdf)) {
      PDFTextStripper st = new PDFTextStripper();
      st.setSortByPosition(true);
      String text = st.getText(doc);
      return text.replace('\u00A0', ' ')
                 .replace("\r\n", "\n")
                 .replace("\r", "\n");
    }
  }

  public static String readPageText(File pdf, int page) throws IOException {
    try (PDDocument doc = PDDocument.load(pdf)) {   //
      PDFTextStripper st = new PDFTextStripper();
      st.setSortByPosition(true);
      st.setStartPage(page);                       // 1-based
      st.setEndPage(page);
      String text = st.getText(doc);
      return text.replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace("\r", "\n");
    }
  }

  public static void debugAround(Logger log, String tag, String block, int centerLine, int window) {
    if (!log.isDebugEnabled() || block == null) return;
    String[] lines = block.split("\\r?\\n");
    int from = Math.max(0, centerLine - window);
    int to   = Math.min(lines.length, centerLine + window);
    String dump = IntStream.range(from, to)
        .mapToObj(i -> String.format("%4d | %s", i, lines[i]))
        .collect(Collectors.joining("\n"));
    log.debug("[debug:{}] lines {}..{} (center={})\n{}", tag, from, to, centerLine, dump);
  }

  public static String readPagesText(File pdf, int startPage, int endPage) throws IOException {
    try (PDDocument doc = PDDocument.load(pdf)) {   //
      PDFTextStripper st = new PDFTextStripper();
      st.setSortByPosition(true);
      st.setStartPage(startPage);
      st.setEndPage(endPage);
      String text = st.getText(doc);
      return text.replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace("\r", "\n");
    }
  }

  private static String norm(String s) {
    if (s == null) return null;
    // 공백과 특수문자 정리, 콤마는 숫자 파싱 전에만 제거
    return s.replace('\u00A0',' ')
            .replace("\r\n","\n")
            .replace("\r","\n");
  }
  
  /**
   * LLM 프롬프트 템플릿 규칙: 숫자 사이 공백 제거
   * 예: "79 5 25" → "79525", "21 6 90" → "21690"
   */
  private static String normalizeCode(String text) {
    if (text == null) return null;
    
    // 연속된 숫자 사이의 공백 제거 (5자리 코드 복원)
    String normalized = text.replaceAll("\\b(\\d)\\s+(\\d)\\s+(\\d)\\s+(\\d)\\s+(\\d)\\b", "$1$2$3$4$5");
    // 3자리 + 2자리 패턴도 처리
    normalized = normalized.replaceAll("\\b(\\d{3})\\s+(\\d{2})\\b", "$1$2");
    // 2자리 + 3자리 패턴도 처리
    normalized = normalized.replaceAll("\\b(\\d{2})\\s+(\\d{3})\\b", "$1$2");
    
    log.debug("코드 정규화: '{}' -> '{}'", text, normalized);
    return normalized;
  }

  /** 3.보험코드 / 4.사업방법 / 5.가입한도 영역 대략 분리 */
  public static Sections splitSections(String text) {
    // “3.”, “4.”, “5.”를 기준으로 매우 느슨하게 블럭 추출
    String[] lines = text.split("\n");
    StringBuilder b3 = new StringBuilder();
    StringBuilder b4 = new StringBuilder();
    StringBuilder b5 = new StringBuilder();

    int mode = 0; // 0:없음 3:보험코드 4:사업방법 5:가입한도
    for (String raw : lines) {
      String s = raw.trim();
      if (s.startsWith("3.")) { mode = 3; continue; }
      if (s.startsWith("4.")) { mode = 4; continue; }
      if (s.startsWith("5.")) { mode = 5; continue; }
      if (mode == 3) b3.append(raw).append('\n');
      else if (mode == 4) b4.append(raw).append('\n');
      else if (mode == 5) b5.append(raw).append('\n');
    }
    return new Sections(b3.toString(), b4.toString(), b5.toString());
  }

  /** 기존 라인형 코드→명칭 파서(백업) */
  public static Map<String,String> parseCodes(String block3) {
    Map<String,String> map = new LinkedHashMap<>();
    if (block3 == null) return map;
    Pattern p = Pattern.compile("(\\(무\\))?\\s*([^\\n]+?)\\s+(\\d{5})(?:\\s|$)");
    Matcher m = p.matcher(block3);
    while (m.find()) {
      String name = m.group(2).trim();
      String code = m.group(3);
      map.put(code, name);
    }
    return map;
  }

  /** 표 형태(2~3열 등) 파서(기존) – 있으면 그대로 사용 */
  public static Map<String,String> parseCodeTable(String block3) {
    log.info("===== parseCodeTable 시작 =====");
    log.info("입력 텍스트 길이: {} 문자", block3 != null ? block3.length() : 0);
    
    Map<String,String> map = new LinkedHashMap<>();
    if (block3 == null) {
      log.warn("입력 텍스트가 null입니다.");
      return map;
    }
    
    // LLM 프롬프트 템플릿 규칙: 윈도우 결합
    // 표가 줄바꿈으로 분절될 수 있으므로 인접 2~3줄 윈도우로 합쳐서 "상품명 + 5자리코드×열" 패턴 탐지
    String[] lines = block3.split("\n");
    log.info("원본 줄 수: {}", lines.length);
    
    // 윈도우 결합 처리
    List<String> combinedLines = combineAdjacentLines(lines);
    log.info("윈도우 결합 후 줄 수: {}", combinedLines.size());
    
    // 느슨한: 한 줄에 5자리 코드가 1개 이상이면 앞쪽을 이름으로 간주
    log.info("처리할 줄 수: {}", combinedLines.size());
    
    for (int i = 0; i < combinedLines.size(); i++) {
      String ln = combinedLines.get(i);
      
      // LLM 프롬프트 템플릿 규칙: 갱신계약 필터링
      if (isRenewalContract(ln)) {
        log.debug("줄 {}: 갱신계약으로 제외 - {}", i + 1, ln.trim());
        continue;
      }
      
      // LLM 프롬프트 템플릿 규칙: 제도성 특약 필터링
      if (isInstitutionalRider(ln)) {
        log.debug("줄 {}: 제도성 특약으로 제외 - {}", i + 1, ln.trim());
        continue;
      }
      
      List<String> codes = findAllCodes(ln);
      if (codes.isEmpty()) {
        log.debug("줄 {}: 코드 없음 - {}", i + 1, ln.trim());
        continue;
      }
      
      String name = ln.replaceAll("\\d{5}", " ").replaceAll("\\s+", " ").trim();
      if (name.isEmpty()) {
        log.debug("줄 {}: 이름 없음 - {}", i + 1, ln.trim());
        continue;
      }
      
      log.info("줄 {}: 이름='{}', 코드={}", i + 1, name, codes);
      for (String cd : codes) {
        map.put(cd, name);
        log.info("  매핑 추가: {} -> {}", cd, name);
      }
    }
    
    log.info("parseCodeTable 결과: {} 개 코드 파싱됨", map.size());
    log.info("===== parseCodeTable 완료 =====");
    return map;
  }

  /** ★ 10P 전용 4열 표 파서(최초/갱신 4개 코드 모두 같은 이름으로 연결) */
  public static Map<String,String> parseCodeTableFourCols(String block3) {
    Map<String,String> map = new LinkedHashMap<>();
    if (block3 == null) return map;

    // 행 안에 5자리 코드가 2개 이상 등장하고, 그 앞에 상품명이 나오는 패턴
    Pattern row = Pattern.compile("^(.*?)(?:\\s+)(\\d{5})(?:\\s+)(\\d{5})(?:\\s+)(\\d{5})(?:\\s+)(\\d{5})\\s*$");
    for (String ln : block3.split("\n")) {
      Matcher m = row.matcher(ln.trim());
      if (!m.find()) continue;
      String name = m.group(1).replaceAll("\\s+", " ").trim();
      if (name.isEmpty()) continue;
      for (int i=2;i<=5;i++) {
        String cd = m.group(i);
        if (cd != null) map.put(cd, name);
      }
    }
    return map;
  }

  /** 코드 주변에서 이름을 추정(백업) */
  public static String fuzzyFindNameByCode(String scopeText, String insuCd) {
    if (scopeText == null || scopeText.isBlank() || insuCd == null) return null;

    // 코드가 포함된 행을 잡아서, 같은 줄/다음 줄에서 괄호/한글/영문 조합 이름을 끌어오는 휴리스틱
    // 예) "21791  (무)흥국생명다사랑3N5간편건강보험(갱신형)"
    Pattern linePat = Pattern.compile("(?m)^.*?\\b" + Pattern.quote(insuCd) + "\\b.*$");
    Matcher lm = linePat.matcher(scopeText);
    while (lm.find()) {
      String line = lm.group();
      String name = extractNameLike(line);
      if (name != null) return name;
    }
    // 바로 다음 줄 힌트도 탐색
    Pattern aroundPat = Pattern.compile("(?ms)^.*?\\b" + Pattern.quote(insuCd) + "\\b.*?\\R(.*)$");
    Matcher am = aroundPat.matcher(scopeText);
    if (am.find()) {
      String next = am.group(1);
      String name = extractNameLike(next);
      if (name != null) return name;
    }
    return null;
  }

  private static String extractNameLike(String s) {
    if (s == null) return null;
    // 괄호/한글/영문/숫자/공백/하이픈 조합을 넉넉히 허용
    Pattern namePat = Pattern.compile("([\\(\\)\\[\\]가-힣A-Za-z0-9\\-\\s]{8,})");
    Matcher nm = namePat.matcher(s);
    while (nm.find()) {
      String cand = nm.group(1).trim();
      // 너무 짧은/의미없는 토막 배제
      if (cand.length() >= 8 && containsKoreanOrParen(cand)) {
        return cand.replaceAll("\\s{2,}", " "); // 공백 정리
      }
    }
    return null;
  }

  private static boolean containsKoreanOrParen(String s) {
    return s.chars().anyMatch(ch ->
        (ch >= 0xAC00 && ch <= 0xD7A3) || ch=='(' || ch==')' || ch=='[' || ch==']');
  }

  private static boolean validName(String s) {
    return s != null && s.length() >= 4 && !s.matches("\\d+");
  }

  private static List<String> findAllCodes(String s) {
    List<String> out = new ArrayList<>();
    if (s == null || s.trim().isEmpty()) {
      log.debug("findAllCodes: 입력 문자열이 비어있음");
      return out;
    }
    
    // 코드 정규화 적용
    String normalized = normalizeCode(s);
    
    Matcher m = Pattern.compile("\\d{5}").matcher(normalized);
    while (m.find()) {
      String code = m.group();
      out.add(code);
      log.debug("findAllCodes: 5자리 코드 발견 - '{}'", code);
    }
    
    log.debug("findAllCodes: '{}'에서 {} 개 코드 발견", normalized.trim(), out.size());
    return out;
  }
  
  /**
   * LLM 프롬프트 템플릿 규칙: 갱신계약 필터링
   * 갱신계약 열/블록은 모두 제외
   */
  private static boolean isRenewalContract(String line) {
    if (line == null) return false;
    
    String lowerLine = line.toLowerCase().trim();
    
    // 갱신계약 관련 키워드 체크
    boolean hasRenewal = lowerLine.contains("갱신");
    boolean hasRenewalContract = lowerLine.contains("갱신계약") || lowerLine.contains("갱신형");
    boolean hasRenewalColumn = lowerLine.contains("갱신)") || lowerLine.contains("갱신]");
    
    boolean isRenewal = hasRenewal && (hasRenewalContract || hasRenewalColumn);
    
    if (isRenewal) {
      log.debug("갱신계약 감지: '{}'", line.trim());
    }
    
    return isRenewal;
  }
  
  /**
   * LLM 프롬프트 템플릿 규칙: 제도성 특약 필터링
   * 제도성 특약, 할증특약 제외
   */
  private static boolean isInstitutionalRider(String line) {
    if (line == null) return false;
    
    String lowerLine = line.toLowerCase().trim();
    
    // 제도성 특약 키워드
    String[] institutionalKeywords = {
      "지정대리청구서비스특약",
      "특정신체부위질병보장제한부인수특약",
      "할증특약",
      "제도성특약"
    };
    
    for (String keyword : institutionalKeywords) {
      if (lowerLine.contains(keyword.toLowerCase())) {
        log.debug("제도성 특약 감지: '{}' (키워드: {})", line.trim(), keyword);
        return true;
      }
    }
    
    return false;
  }
  
  /**
   * LLM 프롬프트 템플릿 규칙: 윈도우 결합
   * 표가 줄바꿈으로 분절될 수 있으므로 인접 2~3줄 윈도우로 합쳐서 "상품명 + 5자리코드×열" 패턴 탐지
   */
  private static List<String> combineAdjacentLines(String[] lines) {
    List<String> combined = new ArrayList<>();
    
    for (int i = 0; i < lines.length; i++) {
      String currentLine = lines[i].trim();
      
      // 현재 줄에 5자리 코드가 있는지 확인
      List<String> currentCodes = findAllCodes(currentLine);
      
      if (!currentCodes.isEmpty()) {
        // 현재 줄에 코드가 있으면 그대로 추가
        combined.add(currentLine);
      } else {
        // 현재 줄에 코드가 없으면 다음 줄들과 결합 시도
        StringBuilder combinedLine = new StringBuilder(currentLine);
        int j = i + 1;
        
        // 최대 2줄까지 결합 시도
        while (j < lines.length && j < i + 3) {
          String nextLine = lines[j].trim();
          List<String> nextCodes = findAllCodes(nextLine);
          
          if (!nextCodes.isEmpty()) {
            // 다음 줄에 코드가 있으면 결합하고 종료
            combinedLine.append(" ").append(nextLine);
            combined.add(combinedLine.toString());
            i = j; // 인덱스 건너뛰기
            break;
          } else {
            // 다음 줄에도 코드가 없으면 계속 결합
            combinedLine.append(" ").append(nextLine);
            j++;
          }
        }
        
        // 결합된 줄에 코드가 있으면 추가
        if (findAllCodes(combinedLine.toString()).size() > 0) {
          combined.add(combinedLine.toString());
        }
      }
    }
    
    log.debug("윈도우 결합: {} 줄 -> {} 줄", lines.length, combined.size());
    return combined;
  }

  /**
   * 가입한도 블록(block5)에서 상품명(productName) 라인을 찾고,
   * 나이에 맞는 밴드(60/65/70/80세 이하) 최대금액과 최저금액을 추출한다.
   * 실패 시 null 반환.
   */
  public static MinMaxLimit parseAgeBandLimitByNameFlexible(String block5, String productName, Integer age) {
    if (block5 == null || productName == null) return null;

    String text = norm(block5);
    // '가입한도' 섹션이 있으면 거기부터 잘라서 탐색
    int pos = text.indexOf("가입한도");
    String sec = (pos >= 0) ? text.substring(pos) : text;

    // 나이에 따른 밴드 인덱스(숫자열에서 0:60세↓, 1:65세↓, 2:70세↓, 3:80세↓ 가정)
    Integer bandIdx = null;
    if (age != null) {
      if (age <= 60) bandIdx = 0;
      else if (age <= 65) bandIdx = 1;
      else if (age <= 70) bandIdx = 2;
      else bandIdx = 3;
    }

    // 상품명 라인 찾기
    String nameRe = buildNameRegexForLimitRow(productName);
    if (nameRe == null) return null;
    Pattern rowPat = Pattern.compile(nameRe, Pattern.CASE_INSENSITIVE);

    String[] lines = sec.split("\n");
    for (String line : lines) {
      String ln = line.trim();
      if (ln.isEmpty()) continue;
      if (!rowPat.matcher(ln).find()) continue;

      // 라인에서 숫자 전부 추출
      List<BigDecimal> nums = new ArrayList<>();
      Matcher m = P_NUM_TOKEN.matcher(ln);
      while (m.find()) {
        String raw = m.group(1);
        String manu = m.group(2); // "만"이면 만원
        BigDecimal v = new BigDecimal(raw.replace(",", ""));
        if ("만".equals(manu)) {
          v = v.multiply(BigDecimal.valueOf(10_000L));
        } else {
          v = toWonByUnit(raw, sec); // 상단 단위 힌트 반영
        }
        nums.add(v);
      }
      if (nums.isEmpty()) continue;

      // 휴리스틱:
      //  - 맨 앞쪽 4개가 60/65/70/80세 이하 최대
      //  - 끝쪽 1~2개가 최저/입력단위(작은 값)인 경우가 많음
      BigDecimal maxBD = null, minBD = null;

      if (nums.size() >= 6) {
        int useIdx = (bandIdx != null) ? Math.max(0, Math.min(3, bandIdx)) : 0;
        maxBD = nums.get(useIdx);

        BigDecimal last = nums.get(nums.size() - 1);
        BigDecimal prev = nums.get(nums.size() - 2);
        // 입력단위(보통 10/50/100 등 소액)로 보이면 그 앞이 '최저'
        if (last.compareTo(BigDecimal.valueOf(200)) <= 0) {
          minBD = prev;
        } else {
          minBD = last;
        }
      } else {
        // 숫자가 모자라면 최소/최대만이라도
        maxBD = nums.stream().max(BigDecimal::compareTo).orElse(null);
        minBD = nums.stream().min(BigDecimal::compareTo).orElse(null);
      }

      return new MinMaxLimit(minBD, maxBD, null, null, null, ln);
    }

    // 못 찾았으면 null
    return null;
  }
  
  
  private static boolean matchAgeBand(Matcher m, int age) {
    // 케이스1: “60세 이하”
    if (m.group(1) != null) {
      int lim = Integer.parseInt(m.group(1));
      return age <= lim;
    }
    // 케이스2: “61~80세”
    if (m.group(3) != null && m.group(4) != null) {
      int a = Integer.parseInt(m.group(3));
      int b = Integer.parseInt(m.group(4));
      return age >= a && age <= b;
    }
    // 케이스3: “70세 이하” 등
    if (m.group(2) != null) {
      int lim = Integer.parseInt(m.group(2));
      return age <= lim;
    }
    return true;
  }

  private static BigDecimal parseManwonToWon(String s) {
    if (s == null) return null;
    long man = Long.parseLong(s.replaceAll("[^\\d]", ""));
    return BigDecimal.valueOf(man).multiply(BigDecimal.valueOf(10_000L)); // 만원→원
  }

  /** ★ 카테고리 추정(한도표의 열 머리말과 맞춤) */
  private static String guessCategoryByName(String canon) {
    if (canon.contains("재해사망")) return "재해\\s*사망";
    if (canon.contains("사망보장")) return "사망\\s*보장";
    if (canon.contains("질병후유장해")) return "질병\\s*후유장해";
    // 기본: 주계약
    return "주\\s*계약";
  }

  /** ★ 한도표용 정규화: 브랜드/괄호/갱신형 제거, 355↔3N5 치환 */
  public static String normalizeForLimit(String s) {
    String x = s;
    x = x.replace(" ", "")
         .replace("(무)", "")
         .replace("흥국생명", "")
         .replace("갱신형", "")
         .replace("주계약", "")
         .replace("특약", "");
    // 355/335/325 → 3n5 로 치환 (한도표의 “3N5”와 통일)
    x = x.replaceAll("\\(3(2|3|5)5\\)", "3n5");
    x = x.replaceAll("3N5", "3n5");
    x = x.toLowerCase();
    return x;
  }

  public static File findPdfForCode(Path dir, String insuCd) {
    if (dir == null || insuCd == null || insuCd.isBlank()) return null;
    File[] arr = dir.toFile().listFiles((d, f) -> f.toLowerCase().endsWith(".pdf"));
    if (arr == null || arr.length == 0) return null;

    for (File pdf : arr) {
      try {
        // 프로젝트에 이미 있는 유틸 메서드: 전체 텍스트 추출
        String text = readAllText(pdf);
        Sections sec = splitSections(text); // 3.보험코드 블록 분리

        // 1) 우선 3번 섹션(block3)에서 코드 탐색
        if (containsCode(sec.block3, insuCd)) return pdf;

        // 2) 폴백: 문서 전체에서 코드가 보이면 매칭으로 간주
        if (containsCode(text, insuCd)) return pdf;

      } catch (Exception ignore) {
        // 읽기 실패한 PDF는 건너뜀
      }
    }
    return null;
  }

  // 선택: 기존 다른 호출부 호환(문자열 경로 버전이 필요하면 같이 추가)
  public static File findPdfForCode(String dir, String insuCd) {
    return (dir == null) ? null : findPdfForCode(Paths.get(dir), insuCd);
  }

  // 내부 헬퍼
  private static boolean containsCode(String text, String code) {
    if (text == null || code == null || code.isBlank()) return false;
    // 숫자 코드의 경계 매칭(단어 경계 \b 사용) + 이스케이프
    Pattern p = Pattern.compile("\\b" + Pattern.quote(code) + "\\b");
    return p.matcher(text).find();
  }

  public static String findFirstRegex(String text, String regex) {
    if (text == null || regex == null || regex.isBlank()) return null;
    Matcher m = Pattern.compile(regex, Pattern.MULTILINE | Pattern.DOTALL).matcher(text);
    return m.find() ? m.group(0).trim() : null;
  }

  // ---- [A] 이름 정규화/퍼지 매칭 유틸 ----
  public static String normalizeForMatch(String s) {
    if (s == null) return "";
    String t = s;
    // 공백/특수기호 제거
    t = t.replaceAll("\\s+", "");
    t = t.replace("(", "").replace(")", "").replace("·", "");
    // 불필요 수식어 제거
    t = t.replace("흥국생명", "").replace("생명", "")
        .replace("주계약", "").replace("특약", "")
        .replace("갱신형", "").replace("보험", "");
    // 355 ↔ 3N5 매핑
    t = t.replace("355", "3N5").replace("325", "3N5").replace("335", "3N5");
    // 간편/건강 등 범용 수식어는 남겨도 무방
    return t;
  }

  public static boolean fuzzyNameHit(String pageName, String targetName) {
    if (pageName == null || targetName == null) return false;
    String a = normalizeForMatch(pageName);
    String b = normalizeForMatch(targetName);
    if (a.isEmpty() || b.isEmpty()) return false;
    if (a.contains(b) || b.contains(a)) return true;

    // 특약 범주 동치(필요 시 확장)
    String[][] aliasGroups = {
      {"재해사망","상해사망","재해사망특약","재해사망보장"},
      {"사망보장","정기","사망특약","정기특약","사망"},
      {"입원","입원의료비","입원특약"}
    };
    for (String[] g : aliasGroups) {
      boolean hitA=false, hitB=false;
      for (String k : g) { if (a.contains(k)) hitA=true; if (b.contains(k)) hitB=true; }
      if (hitA && hitB) return true;
    }
    return false;
  }

  // ---- [B] 연령 밴드 라벨 유도 ----
  private static String bandLabelForAge(int age) {
    if (age <= 60) return "60세";
    if (age <= 65) return "61~65";
    if (age <= 70) return "66~70";
    if (age <= 75) return "71~75";
    if (age <= 80) return "76~80";
    return "81세";
  }

  // ---- [C] 숫자/단위 인식 ----
   // “만/원/구좌” 단위 힌트를 보고 원단위로 환산
  private static BigDecimal toWonByUnit(String raw, String unitHintLine) {
    if (raw == null) return null;
    String n = raw.replace(",","");
    BigDecimal v = new BigDecimal(n);
    String u = unitHintLine != null ? unitHintLine : "";
    boolean hasManwon = u.contains("만원");
    boolean hasWon = u.contains("원");
    boolean hasGujoa = u.contains("구좌");
    // “(가입단위 : 만)”처럼 표 상단에 만 단위를 명시 → 만원 단위로 간주
    if (u.contains("가입단위") && u.contains("만")) {
      return v.multiply(BigDecimal.valueOf(10_000L));
    }

    if (hasGujoa) {
      // "1구좌=100만원" 같은 문구 파악
      Matcher m = Pattern.compile("1\\s*구좌[^0-9]*([0-9,]+)\\s*만원").matcher(u);
      if (m.find()) {
        BigDecimal perUnitWon = new BigDecimal(m.group(1).replaceAll(",", "")).multiply(BigDecimal.valueOf(10_000));
        // n=구좌 수 → 금액
        return perUnitWon.multiply(v);
      }
      // 구좌인데 per-unit 문구가 없다면 보수적으로 만원 단위로 간주
      return v.multiply(BigDecimal.valueOf(10_000));
    }

    if (hasManwon || (!hasWon && v.compareTo(BigDecimal.valueOf(10_000)) < 0)) {
      // 헤더에 '만원'이 있거나, 값이 통상 10~5000 범위면 '만원'으로 해석
      return v.multiply(BigDecimal.valueOf(10_000));
    }

    // 라인 자체에 “100만”처럼 붙어있는 경우는 호출부에서 group(2)로 이미 처리
    return v; // 기본은 원으로 간주
  }

  // ---- [D] 가입한도 파싱: 이름+연령대 기반 ----
  public static class AgeBandLimit {
    public Integer minManwon; // 만원 단위 원래 숫자(표 단위가 만원일 때)
    public Integer maxManwon;
    public Integer stepManwon;
    public String matchedLine; // 디버깅용
    public String matchedBand;
    public BigDecimal minWon;  // 원 단위(계산용)
    public BigDecimal maxWon;
  }

  public static AgeBandLimit parseAgeBandLimitByNameAndAge(String block5, String name, int age) {
    if (block5 == null || name == null) return null;
    String[] lines = block5.split("\\r?\\n");
    if (lines.length == 0) return null;

    // 1) 이름이 있는 라인 찾기(퍼지)
    int nameIdx = -1;
    for (int i=0;i<lines.length;i++) {
      if (fuzzyNameHit(lines[i], name)) { nameIdx = i; break; }
    }
    // 폴백: "다사랑3N5" 등 핵심 키워드
    if (nameIdx < 0) {
      String key = normalizeForMatch(name);
      for (int i=0;i<lines.length;i++) {
        String l = normalizeForMatch(lines[i]);
        if (l.contains("다사랑3N5") || l.contains(key)) { nameIdx = i; break; }
      }
    }
    if (nameIdx < 0) return null;

    // 2) 연령대 밴드 결정 및 주변에서 금액 추출
    String band = bandLabelForAge(age); // 예: "60세"
    Pattern pMin = Pattern.compile("(최저|최소)[^0-9]*([0-9,]+)");
    Pattern pMax = Pattern.compile("(최대)[^0-9]*([0-9,]+)");

    BigDecimal min = null, max = null;
    String unitHint = null;
    int from = Math.max(0, nameIdx-3), to = Math.min(lines.length, nameIdx+12);
    for (int i=from; i<to; i++) {
      String l = lines[i];
      if (unitHint == null && (l.contains("만원") || l.contains("원") || l.contains("구좌"))) unitHint = l;

      // 연령대가 명시된 줄 우선 사용(없으면 인접 줄도 허용)
      boolean bandHit = l.contains(band) || (band.equals("60세") && l.contains("60세이하"));
      if (!bandHit && i > nameIdx) {
        // 표형 구조에서 다음/다다음 줄에 수치만 나오는 경우 허용
        bandHit = (i <= nameIdx + 2);
      }
      if (!bandHit) continue;

      Matcher m1 = pMin.matcher(l);
      Matcher m2 = pMax.matcher(l);
      if (m1.find()) min = toWonByUnit(m1.group(2), unitHint!=null?unitHint:l);
      if (m2.find()) max = toWonByUnit(m2.group(2), unitHint!=null?unitHint:l);

      // "100 / 1000" 같이 헤더에 최저/최대가 분리된 경우
      if (min == null || max == null) {
        Matcher pair = Pattern.compile("([0-9,]{2,})\\s*[/,\\-~]\\s*([0-9,]{2,})").matcher(l);
        if (pair.find()) {
          BigDecimal a = toWonByUnit(pair.group(1), unitHint!=null?unitHint:l);
          BigDecimal b = toWonByUnit(pair.group(2), unitHint!=null?unitHint:l);
          if (a != null && b != null) {
            min = (min == null) ? a : min;
            max = (max == null) ? b : max;
          }
        }
      }

      if (min != null || max != null) {
        AgeBandLimit out = new AgeBandLimit();
        out.matchedLine = l;
        out.matchedBand = band;
        out.minWon = min;
        out.maxWon = max;
        // 만원 수치도 채워두기(표 단위가 만원일 때만 의미)
        if (min != null) out.minManwon = min.divide(BigDecimal.valueOf(10_000)).intValue();
        if (max != null) out.maxManwon = max.divide(BigDecimal.valueOf(10_000)).intValue();
        return out;
      }
    }
    return null;
  }

  private static final Pattern P_NUM = Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)(?=\\s*[만천]?(?:\\s|$))");
  private static final String[] AGE_HEADERS = {"60세이하","60세","61~65","66~70","71~75","76~80","81세 이상","81세"};
  private static final Pattern P_NUM_TOKEN = Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})*|[0-9]+)\\s*(만)?");

 

  private static String buildNameRegexForLimitRow(String productName) {
    if (productName == null) return null;
    String base = productName;

    // “다사랑355간편건강보험” → “다사랑3N5간편건강보험” 케이스 대응
    base = base.replaceAll("3\\s*5\\s*5", "3\\s*[^0-9]?\\s*5"); // 355 → 3N5 유사 매칭

    // 괄호/특수문자 공백 허용
    base = base.replaceAll("\\(", "\\\\(").replaceAll("\\)", "\\\\)");
    base = base.replaceAll("\\s+", "\\\\s*"); // 공백 유연화

    // 앞에 “주계약 ”이 붙을 수도 있음
    return "^(?:주계약\\s*)?" + base + ".*$";
  }

  /** 가입한도 테이블을 라인-정규식으로 직접 파싱하는 폴백 */
  public static MinMaxLimit parseMinMaxLooseByName(String pageText, String productName) {
    if (pageText == null || productName == null) return null;
    String text = norm(pageText);

    // 가입한도 섹션만 추리기 (없으면 전체에서 탐색)
    String sec = text;
    int idx = text.indexOf("가입한도");
    if (idx >= 0) sec = text.substring(idx);

    // 키 라인(상품 라인) 매칭
    String nameRe = buildNameRegexForLimitRow(productName);
    if (nameRe == null) return null;
    Pattern rowPat = Pattern.compile(nameRe, Pattern.CASE_INSENSITIVE);

    String[] lines = sec.split("\n");
    for (String line : lines) {
      String ln = line.trim();
      if (ln.isEmpty()) continue;
      if (!rowPat.matcher(ln).find()) continue;

      // 숫자 토큰 전부 추출
      List<BigDecimal> nums = new ArrayList<>();
      Matcher m = P_NUM_TOKEN.matcher(ln);
      while (m.find()) {
        String raw = m.group(1);
        String manu = m.group(2); // "만" 여부
        BigDecimal v = new BigDecimal(raw.replace(",", ""));
        if ("만".equals(manu)) {
          v = v.multiply(BigDecimal.valueOf(10_000L));
        } else {
          v = toWonByUnit(raw, sec); // 상단 단위 힌트 반영
        }
        nums.add(v);
      }

      if (nums.isEmpty()) continue;

      // 휴리스틱:
      //  - 첫 번째 값 = 60세이하 최대
      //  - 마지막 값은 '입력단위'일 가능성 높음(10/50/100 등 아주 작은 값)
      //  - 그 앞 값 = 최저
      BigDecimal max60 = nums.get(0);
      BigDecimal min   = null;

      if (nums.size() >= 6) {
        BigDecimal last = nums.get(nums.size()-1);
        BigDecimal prev = nums.get(nums.size()-2);
        // '입력단위'는 보통 10·50·100 등 작은 수 → prev 를 최저로 채택
        if (last.compareTo(BigDecimal.valueOf(200)) <= 0) {
          min = prev;
        } else {
          min = last;
        }
      } else if (nums.size() >= 5) {
        min = nums.get(nums.size()-1);
      } else {
        // 숫자가 모자라면 최소만/최대만이라도
        min = nums.stream().min(BigDecimal::compareTo).orElse(null);
      }

      return new MinMaxLimit(min, max60, null, null, null, ln);
    }
    return null;
  }

  // 1) 헤더 라인 찾고 각 밴드의 시작 인덱스(문자열 기준) 수집
  private static Map<String,Integer> findHeaderCols(String[] lines, int start, int lookAhead) {
    for (int i=start; i<Math.min(lines.length, start+lookAhead); i++) {
      String line = lines[i];
      int hit = 0;
      Map<String,Integer> map = new LinkedHashMap<>();
      for (String h : AGE_HEADERS) {
        int idx = line.indexOf(h);
        if (idx >= 0) { map.put(h, idx); hit++; }
      }
      // 2개 이상만 잡혀도 헤더로 인정
      if (hit >= 2) return map;
    }
    return Map.of();
  }

  // 2) 나이→밴드 라벨
  private static String pickBandLabel(int age) {
    if (age <= 60) return "60세이하"; // 문서에 따라 "60세"로만 있을 수도
    if (age <= 65) return "61~65";
    if (age <= 70) return "66~70";
    if (age <= 75) return "71~75";
    if (age <= 80) return "76~80";
    return "81세";
  }

  // 3) 주어진 라인에서, 헤더 인덱스 기준으로 원하는 밴드 구간 잘라 숫자 추출
  private static BigDecimal extractNumberAtBand(String line,String band,Map<String,Integer> headerCols,String unitHint) {
    if (line == null || headerCols == null || headerCols.isEmpty()) return null;

    // 1) 시작 컬럼 결정 (밴드명이 없을 때 보정)
    String key = band;
    Integer fromIdx = headerCols.get(key);
    if (fromIdx == null) {
      if ("60세".equals(band) && headerCols.containsKey("60세이하")) {
        key = "60세이하";
        fromIdx = headerCols.get(key);
      } else if ("60세이하".equals(band) && headerCols.containsKey("60세")) {
        key = "60세";
        fromIdx = headerCols.get(key);
      } else {
        // 가장 왼쪽 컬럼으로 폴백
        fromIdx = headerCols.values().stream().min(Integer::compareTo).orElse(0);
      }
    }

    int from = Math.max(0, Math.min(fromIdx, line.length()));
    // 2) 끝 컬럼 결정: from보다 큰 값 중 최소
    int to = line.length();
    for (Map.Entry<String,Integer> e : headerCols.entrySet()) {
      int v = e.getValue();
      if (v > from && v < to) to = v;
    }

    // 경계 보정
    if (to < from) {
      int t = from; from = to; to = t;
    }
    if (from >= line.length() || to <= from) {
      if (log.isDebugEnabled()) {
        log.debug("[limit][band] invalid slice band={}, from={}, to={}, line='{}'",
            band, from, to, line);
      }
      return null; // 
    }

    String slice = line.substring(from, to);

    if (log.isDebugEnabled()) {
      log.debug("[limit][band] band={}, from={}, to={}, slice='{}'", band, from, to, slice);
    }

    Matcher m = P_NUM.matcher(slice);
    if (m.find()) {
      if (log.isDebugEnabled()) {
        log.debug("[limit][band] matched raw='{}' (unitHint='{}')", m.group(1), unitHint);
      }
      //
      return toWonByUnit(m.group(1), unitHint != null ? unitHint : line);
    } else {
      if (log.isDebugEnabled()) {
        log.debug("[limit][band] NO number in slice for band={}", band);
      }
      return null; //
    }
  }

  // 4) 가입한도 V2: 헤더 정렬 방식
  public static AgeBandLimit parseAgeBandLimitByHeaderColumns(String block5, String name, int age) {
    if (block5 == null || name == null) return null;
    String[] lines = block5.split("\\r?\\n");
    // 4-1) 이름(또는 동치명) 라인 탐색
    int nameIdx = -1;
    for (int i=0;i<lines.length;i++) {
      if (fuzzyNameHit(lines[i], name)) { nameIdx = i; break; }
    }
    if (nameIdx < 0) {
      // 다사랑 3N5 보정키워드
      String key = normalizeForMatch(name);
      for (int i=0;i<lines.length;i++) {
        if (normalizeForMatch(lines[i]).contains("다사랑3N5") || normalizeForMatch(lines[i]).contains(key)) {
          nameIdx = i; break;
        }
      }
    }
    if (nameIdx < 0) return null;

    // 4-2) 헤더 라인과 단위 힌트 탐색
    Map<String,Integer> headerCols = findHeaderCols(lines, Math.max(0,nameIdx-6), 12);
    String unitHint = null;
    for (int i=Math.max(0,nameIdx-6); i<Math.min(lines.length, nameIdx+12); i++) {
      String l = lines[i];
      if (l.contains("만원") || l.contains("원") || l.contains("구좌")) { unitHint = l; break; }
    }

    // 4-3) 이름 아래쪽 몇 줄에서 최저/최대 줄 찾아서 밴드별 숫자 추출
    String band = pickBandLabel(age);
    BigDecimal min = null, max = null;
    for (int i=nameIdx; i<Math.min(lines.length, nameIdx+12); i++) {
      String l = lines[i];
      if (l.contains("최소") || l.contains("최저")) {
        min = extractNumberAtBand(l, band, headerCols, unitHint);
      } else if (l.contains("최대")) {
        max = extractNumberAtBand(l, band, headerCols, unitHint);
      }
      if (min != null && max != null) {
        AgeBandLimit out = new AgeBandLimit();
        out.minWon = min; out.maxWon = max;
        if (min != null) out.minManwon = min.divide(BigDecimal.valueOf(10_000)).intValue();
        if (max != null) out.maxManwon = max.divide(BigDecimal.valueOf(10_000)).intValue();
        out.matchedBand = band; out.matchedLine = l;
        return out;
      }
    }
    return null;
  }

  // 이름 정규화: 비교용 키 만들기
  public static String normalizeTitle(String s) {
    if (s == null) return null;
    String t = s;
    t = t.replaceAll("주계약|특약|흥국생명|갱신형", " ");
    t = t.replaceAll("\\(무\\)", " ");
    t = t.replaceAll("\\([^)]*\\)", " ");   // 괄호안 전부 제거: 다(多)사랑 → 다 사랑 → 공백정리
    t = t.replaceAll("[^가-힣0-9A-Za-z]", " "); // 특수문자 제거
    t = t.replaceAll("\\s+", " ").trim();
    // 대표 패턴 치환(필요시 추가): 다 사랑 → 다사랑
    t = t.replaceAll("다 사랑", "다사랑");
    return t;
  }

  // “억/만/천만/100만” 등의 표현을 원( long/BigDecimal )로 환산
  private static BigDecimal toWonFromKoreanUnit(String token) {
    if (token == null) return null;
    String x = token.replaceAll(",", "").trim();
    if (x.isEmpty()) return null;

    // 억 / 만 / 천만 / 만원 / 원 … 케이스 처리
    // 우선 숫자만 분리
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]+)").matcher(x);
    if (!m.find()) return null;
    BigDecimal n = new BigDecimal(m.group(1));

    if (x.contains("억"))      return n.multiply(new BigDecimal("100000000"));
    if (x.contains("천만"))    return n.multiply(new BigDecimal("10000000"));
    if (x.contains("만"))      return n.multiply(new BigDecimal("10000"));
    if (x.contains("원"))      return n; // 이미 원
    // 특수 케이스: “7000”처럼 단위 없으면 해석 불가 → null
    return null;
  }

  // UW21239 같은 “단순 한도표(가입금액한도/최저가입금액/가입금액단위)” 파서
  public static class SimpleLimitRow {
    public final String rowText;        // 원문 행
    public final String titleNorm;      // 정규화된 행 타이틀
    public final BigDecimal maxWon;     // 가입금액한도(원)
    public final BigDecimal minWon;     // 최저가입금액(원)
    public final BigDecimal stepWon;    // 가입금액단위(원)
    public SimpleLimitRow(String rowText, String titleNorm,
                          BigDecimal maxWon, BigDecimal minWon, BigDecimal stepWon) {
      this.rowText = rowText; this.titleNorm = titleNorm;
      this.maxWon = maxWon; this.minWon = minWon; this.stepWon = stepWon;
    }
  }

  public static List<SimpleLimitRow> parseSimpleLimitTable(String block5) {
    List<SimpleLimitRow> out = new ArrayList<>();
    if (block5 == null) return out;

    // 헤더가 “구분 주보험 및 특약명 가입금액한도 최저가입금액 가입금액단위 …” 형태라는 전제
    String[] lines = block5.split("\\r?\\n");
    for (String line : lines) {
      String L = line.trim();
      if (L.isEmpty()) continue;

      // 숫자/단위가 2~3개 들어가는 행만 후보
      // 예) “주계약 다사랑암보험 1000만 80만 10만”
      java.util.regex.Matcher numM =
          java.util.regex.Pattern.compile("([0-9][0-9,]*\\s*(억|천만|만|원)?)").matcher(L);

      List<String> nums = new ArrayList<>();
      while (numM.find()) nums.add(numM.group().trim());
      if (nums.size() < 2) continue; // min/max가 최소 2개 이상 필요

      // 행 타이틀: 숫자 앞의 텍스트(좌측 구분+명칭)
      String titlePart = L.replaceAll("([0-9][0-9,]*\\s*(억|천만|만|원)?)", " ").replaceAll("\\s+"," ").trim();
      String titleNorm = normalizeTitle(titlePart);
      if (titleNorm == null || titleNorm.isEmpty()) continue;

      BigDecimal max = toWonFromKoreanUnit(nums.get(0));
      BigDecimal min = toWonFromKoreanUnit(nums.size() > 1 ? nums.get(1) : null);
      BigDecimal step = toWonFromKoreanUnit(nums.size() > 2 ? nums.get(2) : null);

      out.add(new SimpleLimitRow(L, titleNorm, max, min, step));
    }
    return out;
  }

  // 정규화된 이름으로 가장 잘 매칭되는 단순 한도 행 찾기
  public static MinMaxLimit parseSimpleLimitByName(String block5, String productNameNorm) {
    List<SimpleLimitRow> rows = parseSimpleLimitTable(block5);
    if (rows.isEmpty() || productNameNorm == null) return null;

    // 1) 완전 포함 매칭 우선
    for (SimpleLimitRow r : rows) {
      if (r.titleNorm.contains(productNameNorm) || productNameNorm.contains(r.titleNorm)) {
        return new MinMaxLimit(r.minWon, r.maxWon, null, null, null, r.rowText);
      }
    }
    // 2) 단어 교집합 기반 루스 매칭(간단)
    String[] words = productNameNorm.split(" ");
    MinMaxLimit best = null; int bestScore = -1;
    for (SimpleLimitRow r : rows) {
      int score = 0;
      for (String w : words) if (!w.isBlank() && r.titleNorm.contains(w)) score++;
      if (score > bestScore) {
        bestScore = score;
        best = new MinMaxLimit(r.minWon, r.maxWon, null, null, null, r.rowText);
      }
    }
    return best;
  }



  /* ===== 작은 DTO들 ===== */

  public static class Sections {
    public final String block3; // 3.보험코드
    public final String block4; // 4.사업방법
    public final String block5; // 5.가입한도
    public Sections(String b3, String b4, String b5) {
      this.block3 = b3; this.block4 = b4; this.block5 = b5;
    }
  }

  /** 가입한도 결과(범위형/구좌형 모두 커버) */
  public static class MinMaxLimit {
    public final BigDecimal minWon;     // 원 단위 최소
    public final BigDecimal maxWon;     // 원 단위 최대
    public final BigDecimal perUnitWon; // 구좌 1구좌 금액(원) — 없으면 null
    public final Integer minUnits;      // 최소 구좌 수(옵션)
    public final Integer maxUnits;      // 최대 구좌 수(옵션)
    public final String matchedLine;    // 원문 라인(디버그)
    public MinMaxLimit(BigDecimal minWon, BigDecimal maxWon,
                       BigDecimal perUnitWon, Integer minUnits, Integer maxUnits,
                       String matchedLine) {
      this.minWon=minWon; this.maxWon=maxWon; this.perUnitWon=perUnitWon;
      this.minUnits=minUnits; this.maxUnits=maxUnits; this.matchedLine=matchedLine;
    }
  }

  public static Map<String, String> parseTerms(String block4) {
    Map<String, String> terms = new LinkedHashMap<>();
    if (block4 == null || block4.trim().isEmpty()) {
      return terms;
    }
    
    // 보험기간 추출
    String insuTerm = extractInsuTerm(block4);
    if (insuTerm != null) {
      terms.put("insuTerm", insuTerm);
    }
    
    // 납입기간 추출
    String payTerm = extractPayTerm(block4);
    if (payTerm != null) {
      terms.put("payTerm", payTerm);
    }
    
    // 가입나이 추출
    String ageRange = extractAgeRange(block4);
    if (ageRange != null) {
      terms.put("ageRange", ageRange);
    }
    
    // 갱신 여부 추출
    String renew = extractRenew(block4);
    if (renew != null) {
      terms.put("renew", renew);
    }
    
    return terms;
  }
  
  private static String extractInsuTerm(String text) {
    // 보험기간 패턴들 (확장 및 개선)
    List<String> results = new ArrayList<>();
    
    // 패턴 1: 종신, 평생
    if (text.contains("종신")) {
      results.add("종신");
    }
    if (text.contains("평생")) {
      results.add("평생");
    }
    
    // 패턴 2: 세만기, 세까지
    String[] agePatterns = {
      "([0-9]+)세만기",
      "([0-9]+)세\\s*까지",
      "([0-9]+)세\\s*종료"
    };
    
    for (String pattern : agePatterns) {
      Pattern p = Pattern.compile(pattern);
      Matcher m = p.matcher(text);
      while (m.find()) {
        String term = m.group(0);
        if (!results.contains(term)) {
          results.add(term);
        }
      }
    }
    
    // 패턴 3: 년만기
    String[] yearPatterns = {
      "([0-9]+)년만기",
      "([0-9]+)년\\s*만기"
    };
    
    for (String pattern : yearPatterns) {
      Pattern p = Pattern.compile(pattern);
      Matcher m = p.matcher(text);
      while (m.find()) {
        String term = m.group(1) + "년";
        if (!results.contains(term)) {
          results.add(term);
        }
      }
    }
    
    return results.isEmpty() ? null : String.join(", ", results);
  }
  
  private static String extractPayTerm(String text) {
    // 납입기간 패턴들 (확장 및 개선)
    List<String> results = new ArrayList<>();
    
    // 전기납, 일시납 패턴 제거 (79525 다사랑암진단특약에는 해당 없음)
    
    // 패턴 2: 년납
    String[] patterns = {
      "([0-9]+)년납",
      "([0-9]+)년\\s*납"
    };
    
    for (String pattern : patterns) {
      Pattern p = Pattern.compile(pattern);
      Matcher m = p.matcher(text);
      while (m.find()) {
        String term = m.group(1) + "년납";
        if (!results.contains(term)) {
          results.add(term);
        }
      }
    }
    
    return results.isEmpty() ? null : String.join(", ", results);
  }
  
  private static String extractAgeRange(String text) {
    // 가입나이 패턴들 (확장 및 개선)
    List<String> results = new ArrayList<>();
    
    String[] patterns = {
      "만\\s*([0-9]+)세\\s*~\\s*([0-9]+)세",
      "([0-9]+)세\\s*~\\s*([0-9]+)세"
    };
    
    for (String pattern : patterns) {
      Pattern p = Pattern.compile(pattern);
      Matcher m = p.matcher(text);
      while (m.find()) {
        String ageRange = m.group(1) + "세~" + m.group(2) + "세";
        if (!results.contains(ageRange)) {
          results.add(ageRange);
        }
      }
    }
    
    return results.isEmpty() ? null : String.join(", ", results);
  }
  
  private static String extractRenew(String text) {
    // 갱신 여부 패턴들 (개선)
    if (text.contains("갱신형") || text.contains("갱신 계약") || text.contains("갱신계약")) {
      return "갱신형";
    }
    if (text.contains("갱신가능") || text.contains("갱신 가능")) {
      return "갱신가능";
    }
    return null;
  }
  
}
