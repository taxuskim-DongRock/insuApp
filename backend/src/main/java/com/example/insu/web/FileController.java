package com.example.insu.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    @Value("${insu.pdf-dir}")
    private String pdfDir;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("파일 업로드 요청: {}", file.getOriginalFilename());
            log.info("파일 크기: {} bytes", file.getSize());
            log.info("파일 타입: {}", file.getContentType());
            
            // PDF 파일인지 확인
            if (!"application/pdf".equals(file.getContentType())) {
                response.put("success", false);
                response.put("message", "PDF 파일만 업로드할 수 있습니다.");
                log.warn("PDF가 아닌 파일 업로드 시도: {}", file.getContentType());
                return ResponseEntity.badRequest().body(response);
            }
            
            // 파일명 검증 (UWXXXX 형식 권장)
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
                response.put("success", false);
                response.put("message", "유효한 PDF 파일명이 아닙니다.");
                log.warn("유효하지 않은 파일명: {}", originalFilename);
                return ResponseEntity.badRequest().body(response);
            }
            
            // 대상 디렉토리 확인 및 생성
            Path targetDir = Paths.get(pdfDir);
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
                log.info("PDF 디렉토리 생성: {}", targetDir);
            }
            
            // 파일 저장
            Path targetPath = targetDir.resolve(originalFilename);
            
            // 파일 잠금 문제 해결을 위한 재시도 로직
            boolean uploadSuccess = false;
            int maxRetries = 3;
            long retryDelayMs = 1000; // 1초
            
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    // 임시 파일명으로 먼저 저장한 후 이름 변경
                    Path tempPath = targetDir.resolve(originalFilename + ".tmp");
                    
                    // 임시 파일에 저장
                    Files.copy(file.getInputStream(), tempPath, StandardCopyOption.REPLACE_EXISTING);
                    
                    // 기존 파일이 있다면 삭제 시도
                    if (Files.exists(targetPath)) {
                        try {
                            Files.delete(targetPath);
                            log.info("기존 파일 삭제 성공: {}", targetPath);
                        } catch (IOException deleteException) {
                            log.warn("기존 파일 삭제 실패 (다른 프로세스가 사용 중): {}", targetPath);
                            // 파일이 사용 중이면 잠시 대기 후 재시도
                            if (attempt < maxRetries) {
                                try {
                                    TimeUnit.MILLISECONDS.sleep(retryDelayMs * attempt);
                                    log.info("재시도 대기 중... ({}ms)", retryDelayMs * attempt);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    throw new IOException("재시도 중 중단됨", ie);
                                }
                                continue;
                            } else {
                                // 최종 시도에서도 실패하면 임시 파일 삭제
                                Files.deleteIfExists(tempPath);
                                throw new IOException("파일이 다른 프로세스에 의해 사용 중입니다. 잠시 후 다시 시도해주세요.");
                            }
                        }
                    }
                    
                    // 임시 파일을 최종 파일명으로 변경
                    Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    uploadSuccess = true;
                    break;
                    
                } catch (IOException e) {
                    log.warn("파일 업로드 시도 {} 실패: {}", attempt, e.getMessage());
                    if (attempt == maxRetries) {
                        throw e;
                    }
                    
                    // 재시도 전 잠시 대기
                    try {
                        TimeUnit.MILLISECONDS.sleep(retryDelayMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("재시도 중 중단됨", ie);
                    }
                }
            }
            
            if (!uploadSuccess) {
                throw new IOException("파일 업로드에 실패했습니다.");
            }
            
            log.info("파일 업로드 성공: {} -> {}", originalFilename, targetPath);
            
            response.put("success", true);
            response.put("message", "파일이 성공적으로 업로드되었습니다.");
            response.put("fileName", originalFilename);
            response.put("filePath", targetPath.toString());
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            log.error("파일 업로드 중 오류 발생", e);
            response.put("success", false);
            
            // 파일 잠금 관련 오류인지 확인
            if (e.getMessage().contains("다른 프로세스가 파일을 사용 중") || 
                e.getMessage().contains("다른 프로세스에 의해 사용 중")) {
                response.put("message", "파일이 다른 프로그램에서 사용 중입니다. 파일을 닫고 다시 시도해주세요.");
                response.put("errorCode", "FILE_IN_USE");
            } else if (e.getMessage().contains("액세스 할 수 없습니다")) {
                response.put("message", "파일 접근 권한이 없습니다. 관리자 권한으로 실행하거나 파일 권한을 확인해주세요.");
                response.put("errorCode", "ACCESS_DENIED");
            } else {
                response.put("message", "파일 업로드 중 오류가 발생했습니다: " + e.getMessage());
                response.put("errorCode", "UPLOAD_ERROR");
            }
            
            return ResponseEntity.internalServerError().body(response);
        } catch (Exception e) {
            log.error("예상치 못한 오류 발생", e);
            response.put("success", false);
            response.put("message", "예상치 못한 오류가 발생했습니다: " + e.getMessage());
            response.put("errorCode", "UNKNOWN_ERROR");
            return ResponseEntity.internalServerError().body(response);
        }
    }
}



