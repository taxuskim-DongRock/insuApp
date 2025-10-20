package com.example.insu.service;

import com.example.insu.util.PdfParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 비동기 파싱 서비스
 * 
 * - 여러 상품 동시 파싱
 * - 응답 시간 단축
 * - 시스템 리소스 효율적 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncParsingService {
    
    private final ImprovedHybridParsingService hybridParsingService;
    
    @Value("${insu.pdf-dir}")
    private String pdfDir;
    
    /**
     * 단일 상품 비동기 파싱
     */
    @Async("parsingExecutor")
    public CompletableFuture<Map<String, String>> parseAsync(String insuCd) {
        log.info("비동기 파싱 시작: {}", insuCd);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                File pdfFile = findPdfFile(insuCd);
                if (pdfFile == null) {
                    log.warn("PDF 파일 없음: {}", insuCd);
                    return getErrorResult("PDF 파일 없음");
                }
                
                Map<String, String> result = hybridParsingService.parseWithMultipleStrategies(pdfFile, insuCd);
                log.info("비동기 파싱 완료: {}", insuCd);
                return result;
                
            } catch (Exception e) {
                log.error("비동기 파싱 실패: {} - {}", insuCd, e.getMessage(), e);
                return getErrorResult("파싱 오류: " + e.getMessage());
            }
        });
    }
    
    /**
     * 여러 상품 동시 파싱
     */
    public CompletableFuture<Map<String, Map<String, String>>> parseMultiple(
            List<String> insuCodes) {
        
        log.info("=== 다중 상품 비동기 파싱 시작: {} 개 ===", insuCodes.size());
        
        // 각 상품코드에 대해 비동기 파싱 시작
        List<CompletableFuture<Map.Entry<String, Map<String, String>>>> futures = 
            insuCodes.stream()
                .map(code -> parseAsync(code)
                    .thenApply(result -> Map.entry(code, result)))
                .collect(Collectors.toList());
        
        // 모든 작업 완료 대기
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                Map<String, Map<String, String>> results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toMap(
                        Map.Entry::getKey, 
                        Map.Entry::getValue,
                        (v1, v2) -> v1, // 중복 시 첫 번째 값 사용
                        LinkedHashMap::new
                    ));
                
                log.info("=== 다중 상품 비동기 파싱 완료: {} 개 ===", results.size());
                return results;
            });
    }
    
    /**
     * PDF 파일 찾기
     */
    private File findPdfFile(String insuCd) {
        try {
            Path dir = Paths.get(pdfDir);
            return PdfParser.findPdfForCode(dir, insuCd);
        } catch (Exception e) {
            log.error("PDF 파일 검색 실패: {} - {}", insuCd, e.getMessage());
            return null;
        }
    }
    
    /**
     * 에러 결과 생성
     */
    private Map<String, String> getErrorResult(String message) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("insuTerm", "—");
        result.put("payTerm", "—");
        result.put("ageRange", "—");
        result.put("renew", "—");
        result.put("specialNotes", message);
        return result;
    }
}

