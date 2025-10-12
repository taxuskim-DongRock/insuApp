// src/main/java/com/example/insu/web/GlobalExceptionHandler.java
package com.example.insu.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(Exception e) {
        log.error("===== 전역 예외 발생 =====", e);
        log.error("예외 타입: {}", e.getClass().getSimpleName());
        log.error("예외 메시지: {}", e.getMessage());
        log.error("스택 트레이스:", e);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", true);
        errorResponse.put("message", e.getMessage());
        errorResponse.put("type", e.getClass().getSimpleName());
        errorResponse.put("timestamp", System.currentTimeMillis());
        
        // 디버깅을 위한 추가 정보
        if (e.getCause() != null) {
            errorResponse.put("cause", e.getCause().getMessage());
            errorResponse.put("causeType", e.getCause().getClass().getSimpleName());
        }
        
        log.error("오류 응답 생성: {}", errorResponse);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("===== 잘못된 인수 예외 발생 =====", e);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", true);
        errorResponse.put("message", "잘못된 요청: " + e.getMessage());
        errorResponse.put("type", "IllegalArgumentException");
        errorResponse.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("===== 런타임 예외 발생 =====", e);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", true);
        errorResponse.put("message", "처리 중 오류 발생: " + e.getMessage());
        errorResponse.put("type", "RuntimeException");
        errorResponse.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
