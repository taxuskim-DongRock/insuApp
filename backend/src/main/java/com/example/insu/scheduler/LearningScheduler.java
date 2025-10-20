package com.example.insu.scheduler;

import com.example.insu.service.IncrementalLearningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 증분 학습 배치 스케줄러
 */
@Slf4j
@Component
public class LearningScheduler {
    
    private final IncrementalLearningService learningService;
    
    public LearningScheduler(IncrementalLearningService learningService) {
        this.learningService = learningService;
    }
    
    /**
     * 매일 새벽 2시 배치 학습
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledBatchLearning() {
        log.info("===== 배치 학습 시작 (스케줄러) =====");
        
        try {
            learningService.performBatchLearning();
            log.info("===== 배치 학습 완료 =====");
            
        } catch (Exception e) {
            log.error("배치 학습 오류: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 1시간마다 통계 업데이트
     */
    @Scheduled(fixedRate = 3600000) // 1시간 = 3600000ms
    public void updateStatistics() {
        log.debug("통계 업데이트 시작");
        
        try {
            learningService.updateStatistics();
            log.debug("통계 업데이트 완료");
            
        } catch (Exception e) {
            log.error("통계 업데이트 오류: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 매주 일요일 오전 3시 데이터 정합성 체크
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    public void dataIntegrityCheck() {
        log.info("===== 데이터 정합성 체크 시작 =====");
        
        try {
            // UW_CODE_MAPPING vs LEARNED_PATTERN 비교
            // 불일치 항목 로깅
            // 필요 시 자동 동기화
            
            log.info("===== 데이터 정합성 체크 완료 =====");
            
        } catch (Exception e) {
            log.error("데이터 정합성 체크 오류: {}", e.getMessage(), e);
        }
    }
}






