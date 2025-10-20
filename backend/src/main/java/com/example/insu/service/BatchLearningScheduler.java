package com.example.insu.service;

import com.example.insu.dto.CorrectionLog;
import com.example.insu.mapper.CorrectionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 배치 학습 스케줄러
 * 
 * - 매일 새벽 2시 자동 배치 학습
 * - 미학습 로그 자동 처리
 * - 통계 자동 업데이트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchLearningScheduler {
    
    private final IncrementalLearningService learningService;
    private final CorrectionLogMapper correctionLogMapper;
    
    private static final DateTimeFormatter FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 매일 새벽 2시 배치 학습 실행
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Async("batchExecutor")
    public void scheduledBatchLearning() {
        String startTime = LocalDateTime.now().format(FORMATTER);
        log.info("╔═══════════════════════════════════════════════════════╗");
        log.info("║  스케줄 배치 학습 시작: {}              ║", startTime);
        log.info("╚═══════════════════════════════════════════════════════╝");
        
        try {
            // 1. 미학습 로그 조회
            List<CorrectionLog> unlearnedLogs = 
                correctionLogMapper.selectUnlearnedLogs(1000);
            
            if (unlearnedLogs.isEmpty()) {
                log.info("✓ 미학습 로그 없음 - 배치 학습 스킵");
                return;
            }
            
            log.info("미학습 로그 {} 건 발견", unlearnedLogs.size());
            
            // 2. 배치 처리 (100개씩)
            int batchSize = 100;
            int totalProcessed = 0;
            int totalSuccess = 0;
            int totalFailed = 0;
            
            for (int i = 0; i < unlearnedLogs.size(); i += batchSize) {
                int batchNumber = (i / batchSize) + 1;
                List<CorrectionLog> batch = unlearnedLogs.subList(
                    i, Math.min(i + batchSize, unlearnedLogs.size())
                );
                
                log.info("배치 {} 처리 시작: {} 건", batchNumber, batch.size());
                
                int batchSuccess = 0;
                int batchFailed = 0;
                
                for (CorrectionLog logEntry : batch) {
                    try {
                        learningService.learnFromCorrection(logEntry);
                        batchSuccess++;
                        totalSuccess++;
                    } catch (Exception e) {
                        log.error("배치 학습 실패: LOG_ID={} - {}", 
                                 logEntry.getId(), e.getMessage());
                        batchFailed++;
                        totalFailed++;
                    }
                }
                
                totalProcessed += batch.size();
                
                log.info("배치 {} 완료: 성공={}, 실패={}", 
                        batchNumber, batchSuccess, batchFailed);
                
                // 메모리 정리 (500건마다)
                if (i > 0 && i % 500 == 0) {
                    log.info("메모리 정리 실행 (처리: {} 건)", totalProcessed);
                    System.gc();
                    
                    // 잠시 대기 (DB 부하 분산)
                    Thread.sleep(1000);
                }
            }
            
            // 3. 통계 업데이트
            log.info("학습 통계 업데이트 시작");
            learningService.updateStatistics();
            log.info("학습 통계 업데이트 완료");
            
            // 4. 결과 요약
            String endTime = LocalDateTime.now().format(FORMATTER);
            log.info("╔═══════════════════════════════════════════════════════╗");
            log.info("║  스케줄 배치 학습 완료: {}              ║", endTime);
            log.info("║  - 총 처리: {} 건                                     ║", totalProcessed);
            log.info("║  - 성공: {} 건                                        ║", totalSuccess);
            log.info("║  - 실패: {} 건                                        ║", totalFailed);
            log.info("║  - 성공률: {}%                                       ║", 
                    totalProcessed > 0 ? (totalSuccess * 100 / totalProcessed) : 0);
            log.info("╚═══════════════════════════════════════════════════════╝");
            
        } catch (Exception e) {
            log.error("스케줄 배치 학습 중 치명적 오류 발생", e);
        }
    }
    
    /**
     * 매시간 통계 업데이트
     */
    @Scheduled(cron = "0 0 * * * *")
    public void hourlyStatisticsUpdate() {
        try {
            log.info("시간별 통계 업데이트 시작");
            learningService.updateStatistics();
            log.info("시간별 통계 업데이트 완료");
        } catch (Exception e) {
            log.error("시간별 통계 업데이트 실패", e);
        }
    }
    
    /**
     * 수동 배치 학습 트리거
     */
    public void triggerManualBatch() {
        log.info("수동 배치 학습 트리거");
        scheduledBatchLearning();
    }
}





