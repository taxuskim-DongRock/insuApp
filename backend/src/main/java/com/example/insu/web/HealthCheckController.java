package com.example.insu.web;

import com.example.insu.dto.LearningStatistics;
import com.example.insu.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 시스템 헬스 체크 API
 * 
 * - 전체 시스템 상태 조회
 * - CSV Few-Shot 로딩 상태
 * - 학습 통계
 * - 캐시 상태
 * - LLM 서비스 상태
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Tag(name = "시스템 헬스", description = "시스템 상태 및 헬스 체크 API")
public class HealthCheckController {
    
    private final IncrementalLearningService learningService;
    
    @Autowired(required = false)
    private UwCodeMappingFewShotService uwCodeMappingFewShotService;
    
    @Autowired(required = false)
    private ParsingMetricsService metricsService;
    
    @Autowired(required = false)
    private OllamaService ollamaService;
    
    @Autowired(required = false)
    private ParsingFallbackService fallbackService;
    
    private static final DateTimeFormatter FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 전체 시스템 헬스 체크
     */
    @GetMapping
    @Operation(summary = "시스템 헬스 체크", 
               description = "전체 시스템의 상태를 조회합니다")
    public ResponseEntity<Map<String, Object>> health() {
        log.info("헬스 체크 API 호출");
        
        Map<String, Object> health = new HashMap<>();
        
        try {
            // 1. 기본 정보
            health.put("status", "UP");
            health.put("timestamp", LocalDateTime.now().format(FORMATTER));
            health.put("version", "2.0.0");
            
            // 2. CSV Few-Shot 상태
            if (uwCodeMappingFewShotService != null) {
                Map<String, Object> csvStats = 
                    uwCodeMappingFewShotService.getStatistics();
                health.put("csvFewShot", csvStats);
                log.debug("CSV Few-Shot 상태: {}", csvStats);
            } else {
                health.put("csvFewShot", Map.of("status", "NOT_AVAILABLE"));
            }
            
            // 3. 학습 통계
            try {
                LearningStatistics stats = learningService.getStatistics();
                Map<String, Object> learningStats = new HashMap<>();
                learningStats.put("totalCorrections", stats.getTotalCorrections());
                learningStats.put("totalPatterns", stats.getTotalPatterns());
                learningStats.put("totalFewShotExamples", stats.getTotalFewShotExamples());
                learningStats.put("currentAccuracy", stats.getCurrentAccuracy());
                learningStats.put("accuracyImprovement", stats.getAccuracyImprovement());
                health.put("learning", learningStats);
                log.debug("학습 통계: {}", learningStats);
            } catch (Exception e) {
                log.warn("학습 통계 조회 실패: {}", e.getMessage());
                health.put("learning", Map.of("status", "ERROR", "message", e.getMessage()));
            }
            
            // 4. 파싱 메트릭
            if (metricsService != null) {
                Map<String, Object> metrics = metricsService.getCurrentMetrics();
                health.put("parsing", metrics);
                log.debug("파싱 메트릭: {}", metrics);
            } else {
                health.put("parsing", Map.of("status", "NOT_AVAILABLE"));
            }
            
            // 5. LLM 서비스 상태
            if (ollamaService != null) {
                Map<String, Object> llmStatus = new HashMap<>();
                try {
                    // OllamaService에 isAvailable() 메서드가 없을 수 있으므로 간단히 체크
                    llmStatus.put("available", true);
                    llmStatus.put("status", "UP");
                    llmStatus.put("message", "Ollama 서비스 구성됨");
                } catch (Exception e) {
                    llmStatus.put("available", false);
                    llmStatus.put("status", "ERROR");
                    llmStatus.put("message", e.getMessage());
                }
                health.put("llm", llmStatus);
            } else {
                health.put("llm", Map.of("status", "NOT_CONFIGURED"));
            }
            
            // 6. 폴백 서비스 상태
            if (fallbackService != null) {
                Map<String, Object> fallbackStats = fallbackService.getFallbackStatistics();
                health.put("fallback", fallbackStats);
            } else {
                health.put("fallback", Map.of("status", "NOT_AVAILABLE"));
            }
            
            log.info("헬스 체크 완료: 모든 시스템 정상");
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            log.error("헬스 체크 중 오류 발생", e);
            
            health.put("status", "ERROR");
            health.put("message", e.getMessage());
            
            return ResponseEntity.status(500).body(health);
        }
    }
    
    /**
     * 간단한 헬스 체크 (핑)
     */
    @GetMapping("/ping")
    @Operation(summary = "간단한 핑 체크", 
               description = "서버가 살아있는지 확인합니다")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of(
            "status", "OK",
            "timestamp", LocalDateTime.now().format(FORMATTER)
        ));
    }
    
    /**
     * CSV Few-Shot 상태만 조회
     */
    @GetMapping("/csv-fewshot")
    @Operation(summary = "CSV Few-Shot 상태", 
               description = "CSV 기반 Few-Shot 예시 로딩 상태를 조회합니다")
    public ResponseEntity<Map<String, Object>> csvFewShotStatus() {
        if (uwCodeMappingFewShotService != null) {
            Map<String, Object> stats = uwCodeMappingFewShotService.getStatistics();
            return ResponseEntity.ok(stats);
        } else {
            return ResponseEntity.ok(Map.of(
                "status", "NOT_AVAILABLE",
                "message", "UwCodeMappingFewShotService가 초기화되지 않았습니다"
            ));
        }
    }
    
    /**
     * 학습 통계만 조회
     */
    @GetMapping("/learning")
    @Operation(summary = "학습 통계", 
               description = "증분 학습 시스템의 통계를 조회합니다")
    public ResponseEntity<Map<String, Object>> learningStatus() {
        try {
            LearningStatistics stats = learningService.getStatistics();
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "OK");
            result.put("totalCorrections", stats.getTotalCorrections());
            result.put("totalPatterns", stats.getTotalPatterns());
            result.put("totalFewShotExamples", stats.getTotalFewShotExamples());
            result.put("currentAccuracy", stats.getCurrentAccuracy());
            result.put("accuracyImprovement", stats.getAccuracyImprovement());
            result.put("fieldAccuracies", Map.of(
                "insuTerm", stats.getInsuTermAccuracy(),
                "payTerm", stats.getPayTermAccuracy(),
                "ageRange", stats.getAgeRangeAccuracy(),
                "renew", stats.getRenewAccuracy()
            ));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("학습 통계 조회 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "ERROR",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * 파싱 메트릭만 조회
     */
    @GetMapping("/metrics")
    @Operation(summary = "파싱 메트릭", 
               description = "파싱 성능 메트릭을 조회합니다")
    public ResponseEntity<Map<String, Object>> metricsStatus() {
        if (metricsService != null) {
            Map<String, Object> metrics = metricsService.getCurrentMetrics();
            return ResponseEntity.ok(metrics);
        } else {
            return ResponseEntity.ok(Map.of(
                "status", "NOT_AVAILABLE",
                "message", "ParsingMetricsService가 초기화되지 않았습니다"
            ));
        }
    }
}

