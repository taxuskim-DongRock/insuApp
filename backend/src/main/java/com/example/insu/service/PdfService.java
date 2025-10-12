// src/main/java/com/example/insu/service/PdfService.java
package com.example.insu.service;

import com.example.insu.dto.CodeEntryDto;
import com.example.insu.dto.PdfFileDto;
import com.example.insu.util.PdfParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;

@Slf4j
@Service
public class PdfService {

    public List<PdfFileDto> listPdfs(Path dir) {
        File[] arr = dir.toFile().listFiles((d,f)-> f.toLowerCase().endsWith(".pdf"));
        if (arr==null) return List.of();
        List<PdfFileDto> out = new ArrayList<>();
        for (File f : arr) out.add(new PdfFileDto(f.getName(), f.length(), new Date(f.lastModified())));
        out.sort(Comparator.comparing(PdfFileDto::getName));
        return out;
    }

    /**
     * 하이브리드 파싱을 위한 PDF 코드 파싱 메서드
     */
    public Map<String, String> parsePdfCodes(File pdfFile) {
        log.info("===== PdfService.parsePdfCodes 시작 =====");
        log.info("PDF 파일: {}", pdfFile.getName());
        
        Map<String, String> result = new LinkedHashMap<>();
        
        try {
            String text = PdfParser.readAllText(pdfFile);
            log.info("PDF 텍스트 길이: {} 문자", text != null ? text.length() : 0);

            PdfParser.Sections sec = PdfParser.splitSections(text);
            log.info("splitSections 결과 - block3 길이: {}", sec.block3 != null ? sec.block3.length() : 0);

            String scope = (sec.block3 != null && !sec.block3.trim().isEmpty() ? sec.block3 : text);
            log.info("사용할 스코프: {}", (sec.block3 != null && !sec.block3.trim().isEmpty()) ? "block3" : "전체 텍스트");

            if (scope == null || scope.trim().isEmpty()) {
                log.warn("스코프가 비어있습니다.");
                return result;
            }

            Map<String, String> parsedCodes = PdfParser.parseCodeTable(scope);
            log.info("파싱된 코드 개수: {}", parsedCodes.size());
            
            result.putAll(parsedCodes);
            
        } catch (Exception e) {
            log.error("PDF 코드 파싱 실패: {}", e.getMessage(), e);
        }
        
        log.info("===== PdfService.parsePdfCodes 완료 =====");
        return result;
    }

    public List<CodeEntryDto> codes(Path pdf, String type) throws IOException {
        log.info("===== PdfService.codes 시작 =====");
        log.info("PDF 경로: {}", pdf);
        log.info("요청 타입: {}", type);
        
        try {
            // 1) PDF 텍스트/섹션 분리
            log.info("PDF 텍스트 읽기 시작...");
            String text = PdfParser.readAllText(pdf.toFile());
            log.info("PDF 텍스트 길이: {} 문자", text != null ? text.length() : 0);
            
            PdfParser.Sections sec = PdfParser.splitSections(text);
            log.info("splitSections 결과 - block3 길이: {}, block4 길이: {}, block5 길이: {}", 
                sec.block3 != null ? sec.block3.length() : 0,
                sec.block4 != null ? sec.block4.length() : 0,
                sec.block5 != null ? sec.block5.length() : 0);
            
            // PDF 내용 샘플 로깅 (디버깅용)
            String[] lines = text.split("\n");
            log.info("PDF 전체 줄 수: {}", lines.length);
            for (int i = 0; i < Math.min(10, lines.length); i++) {
                log.info("PDF 라인 {}: {}", i + 1, lines[i].trim());
            }
            
            String scope = (sec.block3 != null && !sec.block3.trim().isEmpty() ? sec.block3 : text);
            log.info("사용할 스코프: {}", (sec.block3 != null && !sec.block3.trim().isEmpty()) ? "block3" : "전체 텍스트");
            log.info("스코프 텍스트 길이: {} 문자", scope != null ? scope.length() : 0);
            
            // block3이 비어있는 경우 전체 텍스트 사용
            if (scope == null || scope.trim().isEmpty()) {
                log.warn("block3이 비어있어 전체 텍스트를 사용합니다.");
                scope = text;
                log.info("전체 텍스트 사용 후 스코프 길이: {} 문자", scope.length());
            }

            // 2) 전체 코드→명칭 테이블 파싱 (통합본에 존재)
            log.info("코드 테이블 파싱 시작...");
            Map<String,String> all = PdfParser.parseCodeTable(scope);
            if (all == null) {
                log.warn("코드 테이블 파싱 결과가 null입니다.");
                all = new HashMap<>();
            }
            log.info("파싱된 전체 코드 개수: {}", all.size());
            
            if (!all.isEmpty()) {
                log.info("파싱된 전체 코드 목록:");
                all.entrySet().forEach(entry -> 
                    log.info("  - {}: {}", entry.getKey(), entry.getValue())
                );
            }

            // 3) "main" 요청이면 '최초' 표식이 붙은 코드만 필터
            Map<String,String> map = all;
            if ("main".equalsIgnoreCase(type)) {
                log.info("주계약 코드 필터링 시작...");
                Set<String> firstCodes = detectMainCodes(scope); // 아래 헬퍼
                log.info("감지된 '최초' 표식 코드 개수: {}", firstCodes.size());
                log.info("감지된 '최초' 표식 코드 목록: {}", firstCodes);
                
                if (!firstCodes.isEmpty()) {
                    Map<String,String> onlyMain = new LinkedHashMap<>();
                    for (var e : all.entrySet()) {
                        if (firstCodes.contains(e.getKey())) {
                            onlyMain.put(e.getKey(), e.getValue());
                        }
                    }
                    map = onlyMain;
                    log.info("필터링된 주계약 코드 개수: {}", onlyMain.size());
                } else {
                    log.warn("'최초' 표식을 찾지 못함. 전체 코드 반환 (폴백)");
                }
                // '최초' 마커를 못 찾으면 전체 반환(폴백) — 기존 동작과 동일
            }

            // 4) DTO 리스트로 변환 + 정렬
            log.info("DTO 변환 및 정렬 시작...");
            List<CodeEntryDto> list = new ArrayList<>();
            if (map != null) {
                map.forEach((cd,nm)-> {
                    // 주계약인 경우 상품명에 "(주계약)" 표시 추가
                    String displayName = nm;
                    if ("main".equalsIgnoreCase(type)) {
                        if (!nm.contains("주계약") && !nm.contains("최초")) {
                            displayName = nm + " (주계약)";
                            log.debug("주계약 표시 추가: {} -> {}", nm, displayName);
                        }
                    }
                    list.add(new CodeEntryDto(cd, displayName));
                });
            }
            list.sort(Comparator.comparing(CodeEntryDto::getInsuCd));
            
            log.info("최종 결과 코드 개수: {}", list.size());
            log.info("최종 결과 코드 목록:");
            list.forEach(code -> 
                log.info("  - {}: {}", code.getInsuCd(), code.getName())
            );
            
            log.info("===== PdfService.codes 완료 =====");
            return list;
            
        } catch (Exception e) {
            log.error("PdfService.codes 처리 중 오류 발생", e);
            log.error("오류 상세 - PDF: {}, 타입: {}, 오류: {}", pdf, type, e.getMessage());
            throw new IOException("PDF 코드 파싱 실패: " + e.getMessage(), e);
        }
    }

    /**
     * block3(없으면 전체 텍스트)에서 ‘최초’ 표식이 붙은 코드(5자리)만 골라낸다.
     * - 예: "21781 ... 최초", "21781  최초계약", "21781  최초 가입" 등 변형 대응
     */
    private static Set<String> detectMainCodes(String block) {
        log.info("===== detectMainCodes 시작 =====");
        log.info("입력 텍스트 길이: {} 문자", block != null ? block.length() : 0);
        
        Set<String> set = new HashSet<>();
        if (block == null || block.isBlank()) {
            log.warn("입력 텍스트가 null이거나 비어있음");
            return set;
        }

        log.info("첫 번째 패턴 검색 시작: 5자리 코드 + '최초' 패턴");
        // 라인 안에 5자리 코드 + '최초'(또는 최초계약/최초 가입 등) 가 같이 있는 패턴
        // 멀티라인 모드, 코드와 '최초'가 같은 줄에 있으면 매칭
        Pattern p = Pattern.compile("(?m)\\b(\\d{5})\\b[^\\n]{0,80}?(최초|최초\\s*계약|최초\\s*가입)");
        Matcher m = p.matcher(block);
        int matchCount = 0;
        while (m.find()) {
            String foundCode = m.group(1);
            String foundText = m.group(0);
            set.add(foundCode);
            matchCount++;
            log.info("  매치 {}: 코드={}, 전체텍스트={}", matchCount, foundCode, foundText.trim());
        }
        log.info("첫 번째 패턴으로 찾은 코드 개수: {}", set.size());

        // 보너스: 표 머리글이 "구분  최초/갱신 ..." 형태일 때, 아래 줄들이 열 형태로 나열되면
        // 코드 오른쪽 열에 '최초'가 붙는 케이스를 추가로 잡아보는 약한 휴리스틱
        if (set.isEmpty() && block.contains("최초") && block.contains("갱신")) {
            log.info("첫 번째 패턴에서 결과 없음. 두 번째 패턴 검색 시작: 표 형태 패턴");
            Pattern p2 = Pattern.compile("(?m)^\\s*(\\d{5})\\s+.*?최초");
            Matcher m2 = p2.matcher(block);
            int matchCount2 = 0;
            while (m2.find()) {
                String foundCode = m2.group(1);
                String foundText = m2.group(0);
                set.add(foundCode);
                matchCount2++;
                log.info("  매치 {}: 코드={}, 전체텍스트={}", matchCount2, foundCode, foundText.trim());
            }
            log.info("두 번째 패턴으로 찾은 코드 개수: {}", matchCount2);
        } else if (set.isEmpty()) {
            log.warn("'최초' 또는 '갱신' 텍스트가 포함되지 않음. 텍스트 샘플: {}", 
                block.length() > 200 ? block.substring(0, 200) + "..." : block);
        }

        log.info("최종 감지된 주계약 코드 개수: {}", set.size());
        log.info("최종 감지된 주계약 코드 목록: {}", set);
        log.info("===== detectMainCodes 완료 =====");
        return set;
    }
}

