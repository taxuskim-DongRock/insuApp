package com.example.insu.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * UW_CODE_MAPPING CSV 파일을 Few-Shot 예시로 활용하는 서비스
 * 
 * - CSV 파일에서 검증된 Few-Shot 예시를 로드
 * - 상품코드별로 대표 예시 선택
 * - LLM 프롬프트에 통합 가능한 형식으로 제공
 */
@Slf4j
@Service
public class UwCodeMappingFewShotService {
    
    @Value("${uw.csv.path:C:/insu_app/insuCsv}")
    private String csvPath;
    
    @Value("${uw.csv.encoding:EUC-KR}")
    private String csvEncoding;
    
    // 상품코드별 Few-Shot 예시 캐시
    private final Map<String, List<UwCodeMappingRow>> fewShotCache = new ConcurrentHashMap<>();
    
    // 전체 Few-Shot 예시 (문서별로 그룹화)
    private final Map<String, List<UwCodeMappingRow>> documentExamples = new ConcurrentHashMap<>();
    
    // 품질 검증기 (Lazy 초기화)
    private FewShotQualityValidator qualityValidator;
    
    /**
     * 품질 검증기 설정
     */
    public void setQualityValidator(FewShotQualityValidator validator) {
        this.qualityValidator = validator;
        log.info("Few-Shot 품질 검증기 설정 완료");
    }
    
    /**
     * CSV 폴더에서 모든 Few-Shot 예시 로드
     */
    @PostConstruct
    public void loadFewShotExamples() {
        // 품질 검증기 초기화 (Lazy)
        if (qualityValidator == null) {
            qualityValidator = new FewShotQualityValidator();
        }
        try {
            Path dirPath = Paths.get(csvPath);
            
            if (!Files.exists(dirPath)) {
                log.warn("UW_CODE_MAPPING CSV 디렉토리가 존재하지 않음: {}", csvPath);
                log.warn("Few-Shot 예시 로드를 건너뜁니다.");
                return;
            }
            
            log.info("=== UW_CODE_MAPPING CSV Few-Shot 로딩 시작 ===");
            log.info("CSV 디렉토리 경로: {}", csvPath);
            log.info("CSV 인코딩: {}", csvEncoding);
            
            List<UwCodeMappingRow> allRows = new ArrayList<>();
            int fileCount = 0;
            
            // 디렉토리인 경우 모든 CSV 파일 읽기
            if (Files.isDirectory(dirPath)) {
                List<Path> csvFiles = Files.list(dirPath)
                    .filter(p -> p.toString().toLowerCase().endsWith(".csv"))
                    .sorted()
                    .collect(Collectors.toList());
                
                log.info("발견된 CSV 파일 수: {}", csvFiles.size());
                
                for (Path csvFile : csvFiles) {
                    try {
                        List<UwCodeMappingRow> rows = loadCsvFile(csvFile);
                        allRows.addAll(rows);
                        fileCount++;
                        log.info("  [{}] {} - {} 레코드 로드", fileCount, csvFile.getFileName(), rows.size());
                    } catch (Exception e) {
                        log.error("CSV 파일 로드 실패: {} - {}", csvFile.getFileName(), e.getMessage());
                    }
                }
            } else {
                // 단일 파일인 경우
                allRows = loadCsvFile(dirPath);
                fileCount = 1;
            }
            
            log.info("총 {} 개 파일에서 {} 개의 CSV 레코드 로드", fileCount, allRows.size());
            
            // 상품코드별로 그룹화
            Map<String, List<UwCodeMappingRow>> groupedByCode = allRows.stream()
                .collect(Collectors.groupingBy(UwCodeMappingRow::getCode));
            
            // 문서별로 그룹화
            Map<String, List<UwCodeMappingRow>> groupedByDoc = allRows.stream()
                .collect(Collectors.groupingBy(UwCodeMappingRow::getSrcFile));
            
            fewShotCache.putAll(groupedByCode);
            documentExamples.putAll(groupedByDoc);
            
            log.info("상품코드별 그룹: {} 개", fewShotCache.size());
            log.info("문서별 그룹: {} 개", documentExamples.size());
            log.info("=== UW_CODE_MAPPING CSV Few-Shot 로딩 완료 ===");
            
        } catch (Exception e) {
            log.error("CSV Few-Shot 로딩 실패: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 단일 CSV 파일 로드
     */
    private List<UwCodeMappingRow> loadCsvFile(Path csvFile) throws Exception {
        List<UwCodeMappingRow> rows = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(
                new java.io.InputStreamReader(
                    new java.io.FileInputStream(csvFile.toFile()), 
                    csvEncoding))) {
            
            String line;
            boolean isHeader = true;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                if (isHeader) {
                    isHeader = false;
                    log.debug("CSV 헤더: {}", line);
                    continue;
                }
                
                try {
                    UwCodeMappingRow row = parseCsvLine(line);
                    if (row != null && row.getCode() != null && !row.getCode().isEmpty()) {
                        // 품질 검증 수행
                        if (qualityValidator != null) {
                            FewShotQualityValidator.ValidationResult validation = 
                                qualityValidator.validateFewShot(row);
                            
                            if (validation.isValid()) {
                                rows.add(row);
                                log.trace("CSV 레코드 품질 검증 통과: {} (점수: {})", 
                                         row.getCode(), validation.getQualityScore());
                            } else {
                                log.warn("CSV 레코드 품질 검증 실패: {} - {}", 
                                        row.getCode(), validation.getErrors());
                            }
                        } else {
                            // 검증기가 없으면 그대로 추가
                            rows.add(row);
                        }
                    }
                } catch (Exception e) {
                    log.debug("CSV 라인 {} 파싱 실패: {} - {}", lineNumber, line, e.getMessage());
                }
            }
        }
        
        return rows;
    }
    
    /**
     * CSV 라인 파싱
     */
    private UwCodeMappingRow parseCsvLine(String line) {
        // CSV 형식: CODE,PRODUCT_NAME,PRODUCT_GROUP,TYPE_LABEL,MAIN_CODE,PERIOD_LABEL,PERIOD_VALUE,PERIOD_KIND,PAY_TERM,ENTRY_AGE_M,ENTRY_AGE_F,CLASS_TAG,SRC_FILE
        String[] parts = line.split(",", -1); // -1: 빈 값도 포함
        
        if (parts.length < 13) {
            log.warn("CSV 컬럼 수 부족: {} (기대: 13)", parts.length);
            return null;
        }
        
        return UwCodeMappingRow.builder()
            .code(parts[0].trim())
            .productName(parts[1].trim())
            .productGroup(parts[2].trim())
            .typeLabel(parts[3].trim())
            .mainCode(parts[4].trim())
            .periodLabel(parts[5].trim())
            .periodValue(parts[6].trim())
            .periodKind(parts[7].trim())
            .payTerm(parts[8].trim())
            .entryAgeM(parts[9].trim())
            .entryAgeF(parts[10].trim())
            .classTag(parts[11].trim())
            .srcFile(parts[12].trim())
            .build();
    }
    
    /**
     * 특정 상품코드의 Few-Shot 예시 조회
     */
    public List<UwCodeMappingRow> getFewShotExamples(String insuCd) {
        return fewShotCache.getOrDefault(insuCd, Collections.emptyList());
    }
    
    /**
     * 특정 문서의 Few-Shot 예시 조회
     */
    public List<UwCodeMappingRow> getDocumentExamples(String docId) {
        return documentExamples.getOrDefault(docId, Collections.emptyList());
    }
    
    /**
     * 랜덤 Few-Shot 예시 선택 (다양성 확보)
     */
    public List<UwCodeMappingRow> getRandomExamples(int count) {
        List<UwCodeMappingRow> allExamples = fewShotCache.values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toList());
        
        if (allExamples.isEmpty()) {
            return Collections.emptyList();
        }
        
        Collections.shuffle(allExamples);
        return allExamples.stream()
            .limit(count)
            .collect(Collectors.toList());
    }
    
    /**
     * 주계약 Few-Shot 예시만 선택
     */
    public List<UwCodeMappingRow> getMainContractExamples(int count) {
        List<UwCodeMappingRow> mainExamples = fewShotCache.values().stream()
            .flatMap(List::stream)
            .filter(row -> "주계약".equals(row.getProductGroup()) || "MAIN".equals(row.getClassTag()))
            .collect(Collectors.toList());
        
        Collections.shuffle(mainExamples);
        return mainExamples.stream()
            .limit(count)
            .collect(Collectors.toList());
    }
    
    /**
     * Few-Shot 예시를 LLM 프롬프트용 CSV 형식으로 변환
     */
    public String formatAsCsv(List<UwCodeMappingRow> examples) {
        if (examples.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        
        // CSV 헤더
        sb.append("CODE,PRODUCT_NAME,PRODUCT_GROUP,TYPE_LABEL,MAIN_CODE,");
        sb.append("PERIOD_LABEL,PERIOD_VALUE,PERIOD_KIND,PAY_TERM,");
        sb.append("ENTRY_AGE_M,ENTRY_AGE_F,CLASS_TAG,SRC_FILE\n");
        
        // CSV 데이터
        for (UwCodeMappingRow row : examples) {
            sb.append(row.toCsvLine()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 통계 정보 조회
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        int totalExamples = fewShotCache.values().stream()
            .mapToInt(List::size)
            .sum();
        
        long mainCount = fewShotCache.values().stream()
            .flatMap(List::stream)
            .filter(row -> "주계약".equals(row.getProductGroup()))
            .count();
        
        long riderCount = fewShotCache.values().stream()
            .flatMap(List::stream)
            .filter(row -> "선택특약".equals(row.getProductGroup()))
            .count();
        
        stats.put("totalExamples", totalExamples);
        stats.put("uniqueProducts", fewShotCache.size());
        stats.put("documents", documentExamples.size());
        stats.put("mainContracts", mainCount);
        stats.put("riders", riderCount);
        stats.put("csvPath", csvPath);
        
        return stats;
    }
    
    /**
     * UW_CODE_MAPPING CSV 행 DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class UwCodeMappingRow {
        private String code;
        private String productName;
        private String productGroup;
        private String typeLabel;
        private String mainCode;
        private String periodLabel;
        private String periodValue;
        private String periodKind;
        private String payTerm;
        private String entryAgeM;
        private String entryAgeF;
        private String classTag;
        private String srcFile;
        
        public String toCsvLine() {
            return String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                code, productName, productGroup, typeLabel, mainCode,
                periodLabel, periodValue, periodKind, payTerm,
                entryAgeM, entryAgeF, classTag, srcFile);
        }
        
        public String toJsonExample() {
            return String.format("""
                {
                  "code": "%s",
                  "productName": "%s",
                  "productGroup": "%s",
                  "periodLabel": "%s",
                  "payTerm": "%s",
                  "ageRange": "남: %s, 여: %s"
                }""",
                code, productName, productGroup, periodLabel, payTerm, entryAgeM, entryAgeF);
        }
    }
}

