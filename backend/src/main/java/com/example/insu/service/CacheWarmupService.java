package com.example.insu.service;

import com.example.insu.util.PdfParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 캐시 워밍업 서비스
 * 
 * 애플리케이션 시작 시 자주 사용되는 상품의 파싱 결과를 미리 캐시에 로드하여
 * 첫 요청 시 응답 시간을 단축
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheWarmupService {
    
    private final ImprovedHybridParsingService hybridParsingService;
    
    @Value("${insu.pdf-dir}")
    private String pdfDir;
    
    @Value("${cache.warmup.enabled:true}")
    private boolean warmupEnabled;
    
    @Value("${cache.warmup.top-products:50}")
    private int topProductsCount;
    
    /**
     * 애플리케이션 시작 시 캐시 워밍업 실행
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async("batchExecutor")
    public void warmupCache() {
        if (!warmupEnabled) {
            log.info("캐시 워밍업이 비활성화되어 있습니다");
            return;
        }
        
        log.info("╔═══════════════════════════════════════════════════════╗");
        log.info("║          캐시 워밍업 시작                              ║");
        log.info("╚═══════════════════════════════════════════════════════╝");
        
        try {
            // 잠시 대기 (다른 초기화 작업 완료 대기)
            Thread.sleep(5000);
            
            // 1. PDF 파일 목록 조회
            List<String> productCodes = scanPdfFiles();
            log.info("발견된 PDF 파일 수: {}", productCodes.size());
            
            if (productCodes.isEmpty()) {
                log.warn("PDF 파일이 없어 캐시 워밍업을 건너뜁니다");
                return;
            }
            
            // 2. 상위 N개 상품 선택 (파일명 순서대로)
            List<String> topProducts = productCodes.stream()
                .limit(topProductsCount)
                .collect(Collectors.toList());
            
            log.info("캐시 워밍업 대상: {} 개 상품", topProducts.size());
            
            // 3. 병렬 처리로 캐시 로드
            int successCount = 0;
            int failCount = 0;
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < topProducts.size(); i++) {
                String insuCd = topProducts.get(i);
                
                try {
                    File pdfFile = findPdfFile(insuCd);
                    if (pdfFile != null) {
                        hybridParsingService.parseWithMultipleStrategies(pdfFile, insuCd);
                        successCount++;
                        
                        if ((i + 1) % 10 == 0) {
                            log.info("캐시 워밍업 진행: {}/{} ({} 성공, {} 실패)", 
                                    i + 1, topProducts.size(), successCount, failCount);
                        }
                    } else {
                        failCount++;
                        log.debug("PDF 파일 없음: {}", insuCd);
                    }
                    
                    // CPU 부하 분산을 위한 짧은 대기
                    if ((i + 1) % 5 == 0) {
                        Thread.sleep(100);
                    }
                    
                } catch (Exception e) {
                    failCount++;
                    log.warn("캐시 워밍업 실패: {} - {}", insuCd, e.getMessage());
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("╔═══════════════════════════════════════════════════════╗");
            log.info("║          캐시 워밍업 완료                              ║");
            log.info("║  - 대상: {} 개 상품                                   ║", topProducts.size());
            log.info("║  - 성공: {} 개                                        ║", successCount);
            log.info("║  - 실패: {} 개                                        ║", failCount);
            log.info("║  - 소요 시간: {} 초                                   ║", duration / 1000);
            log.info("║  - 평균 처리 시간: {} ms/건                           ║", 
                    successCount > 0 ? (duration / successCount) : 0);
            log.info("╚═══════════════════════════════════════════════════════╝");
            
        } catch (Exception e) {
            log.error("캐시 워밍업 중 치명적 오류 발생", e);
        }
    }
    
    /**
     * PDF 파일 스캔
     */
    private List<String> scanPdfFiles() {
        List<String> productCodes = new ArrayList<>();
        
        try {
            Path dir = Paths.get(pdfDir);
            File[] files = dir.toFile().listFiles((d, name) -> 
                name.toLowerCase().endsWith(".pdf"));
            
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    // UW12345.pdf -> 12345 추출
                    if (fileName.matches("UW\\d{5}\\.pdf")) {
                        String code = fileName.substring(2, 7);
                        productCodes.add(code);
                    }
                }
            }
            
            // 정렬 (코드 순서)
            Collections.sort(productCodes);
            
        } catch (Exception e) {
            log.error("PDF 파일 스캔 실패: {}", e.getMessage(), e);
        }
        
        return productCodes;
    }
    
    /**
     * PDF 파일 찾기
     */
    private File findPdfFile(String insuCd) {
        try {
            Path dir = Paths.get(pdfDir);
            return PdfParser.findPdfForCode(dir, insuCd);
        } catch (Exception e) {
            log.debug("PDF 파일 검색 실패: {} - {}", insuCd, e.getMessage());
            return null;
        }
    }
    
    /**
     * 수동 캐시 워밍업 트리거
     */
    public void triggerManualWarmup() {
        log.info("수동 캐시 워밍업 트리거");
        warmupCache();
    }
}

