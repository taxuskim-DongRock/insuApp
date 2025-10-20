package com.example.insu.service;

import com.example.insu.util.PdfParser;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 파싱 성능 메트릭 수집 서비스
 * 
 * - 파싱 시간 추적
 * - 성공/실패 카운터
 * - 전략별 성능 비교
 * - 실시간 모니터링
 */
@Slf4j
@Service
public class ParsingMetricsService {
    
    // 전략별 메트릭
    private final Map<String, StrategyMetrics> strategyMetrics = new ConcurrentHashMap<>();
    
    // 전체 메트릭
    private final AtomicInteger totalAttempts = new AtomicInteger(0);
    private final AtomicInteger totalSuccess = new AtomicInteger(0);
    private final AtomicInteger totalFailures = new AtomicInteger(0);
    private final AtomicLong totalDuration = new AtomicLong(0);
    
    @Value("${insu.pdf-dir}")
    private String pdfDir;
    
    private static final DateTimeFormatter FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 파싱 메트릭 기록
     */
    public void recordParsing(String strategy, long durationMs, boolean success) {
        // 전략별 메트릭 업데이트
        StrategyMetrics metrics = strategyMetrics.computeIfAbsent(
            strategy, k -> new StrategyMetrics(strategy)
        );
        
        metrics.recordAttempt(durationMs, success);
        
        // 전체 메트릭 업데이트
        totalAttempts.incrementAndGet();
        if (success) {
            totalSuccess.incrementAndGet();
        } else {
            totalFailures.incrementAndGet();
        }
        totalDuration.addAndGet(durationMs);
        
        log.debug("파싱 메트릭 기록: {} - {}ms ({})", 
                 strategy, durationMs, success ? "성공" : "실패");
    }
    
    /**
     * 1분마다 메트릭 요약 출력
     */
    @Scheduled(fixedRate = 60000)
    public void reportMetrics() {
        if (totalAttempts.get() == 0) {
            return; // 메트릭이 없으면 스킵
        }
        
        log.info("╔═══════════════════════════════════════════════════════╗");
        log.info("║          파싱 성능 메트릭 ({})           ║", 
                LocalDateTime.now().format(FORMATTER));
        log.info("╠═══════════════════════════════════════════════════════╣");
        log.info("║  전체 통계:                                            ║");
        log.info("║    - 총 시도: {} 건                                   ║", totalAttempts.get());
        log.info("║    - 성공: {} 건 ({}%)                               ║", 
                totalSuccess.get(), 
                calculatePercentage(totalSuccess.get(), totalAttempts.get()));
        log.info("║    - 실패: {} 건 ({}%)                               ║", 
                totalFailures.get(),
                calculatePercentage(totalFailures.get(), totalAttempts.get()));
        log.info("║    - 평균 처리 시간: {} ms                            ║", 
                totalAttempts.get() > 0 ? (totalDuration.get() / totalAttempts.get()) : 0);
        log.info("╠═══════════════════════════════════════════════════════╣");
        log.info("║  전략별 성능:                                          ║");
        
        // 전략별 메트릭 출력 (성공률 높은 순)
        strategyMetrics.values().stream()
            .sorted((m1, m2) -> Double.compare(m2.getSuccessRate(), m1.getSuccessRate()))
            .forEach(metrics -> {
                log.info("║    [{:20s}]                                  ║", metrics.getStrategy());
                log.info("║      시도: {} 건, 성공률: {}%, 평균: {} ms        ║",
                        metrics.getAttempts(),
                        (int)(metrics.getSuccessRate() * 100),
                        metrics.getAverageDuration());
            });
        
        log.info("╚═══════════════════════════════════════════════════════╝");
    }
    
    /**
     * 메트릭 초기화
     */
    public void resetMetrics() {
        strategyMetrics.clear();
        totalAttempts.set(0);
        totalSuccess.set(0);
        totalFailures.set(0);
        totalDuration.set(0);
        log.info("파싱 메트릭 초기화 완료");
    }
    
    /**
     * 현재 메트릭 조회
     */
    public Map<String, Object> getCurrentMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        metrics.put("totalAttempts", totalAttempts.get());
        metrics.put("totalSuccess", totalSuccess.get());
        metrics.put("totalFailures", totalFailures.get());
        metrics.put("successRate", calculatePercentage(totalSuccess.get(), totalAttempts.get()));
        metrics.put("averageDuration", 
                   totalAttempts.get() > 0 ? (totalDuration.get() / totalAttempts.get()) : 0);
        
        // 전략별 메트릭
        Map<String, Map<String, Object>> strategyStats = new HashMap<>();
        strategyMetrics.forEach((name, m) -> {
            Map<String, Object> stats = new HashMap<>();
            stats.put("attempts", m.getAttempts());
            stats.put("success", m.getSuccess());
            stats.put("failures", m.getFailures());
            stats.put("successRate", m.getSuccessRate() * 100);
            stats.put("averageDuration", m.getAverageDuration());
            strategyStats.put(name, stats);
        });
        metrics.put("strategies", strategyStats);
        
        return metrics;
    }
    
    /**
     * 백분율 계산
     */
    private int calculatePercentage(int value, int total) {
        return total > 0 ? (value * 100 / total) : 0;
    }
    
    /**
     * PDF 파일 찾기
     */
    private File findPdfFile(String insuCd) {
        try {
            Path dir = Paths.get(pdfDir);
            return PdfParser.findPdfForCode(dir, insuCd);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 전략별 메트릭 DTO
     */
    @Data
    private static class StrategyMetrics {
        private final String strategy;
        private final AtomicInteger attempts = new AtomicInteger(0);
        private final AtomicInteger success = new AtomicInteger(0);
        private final AtomicInteger failures = new AtomicInteger(0);
        private final AtomicLong totalDuration = new AtomicLong(0);
        
        public StrategyMetrics(String strategy) {
            this.strategy = strategy;
        }
        
        public void recordAttempt(long durationMs, boolean isSuccess) {
            attempts.incrementAndGet();
            if (isSuccess) {
                success.incrementAndGet();
            } else {
                failures.incrementAndGet();
            }
            totalDuration.addAndGet(durationMs);
        }
        
        public double getSuccessRate() {
            int total = attempts.get();
            return total > 0 ? (double) success.get() / total : 0.0;
        }
        
        public long getAverageDuration() {
            int total = attempts.get();
            return total > 0 ? (totalDuration.get() / total) : 0;
        }
    }
}

