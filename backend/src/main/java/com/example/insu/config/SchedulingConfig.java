package com.example.insu.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 설정
 * 
 * - 배치 학습 스케줄러
 * - 통계 업데이트 스케줄러
 * - 캐시 정리 스케줄러
 */
@Slf4j
@Configuration
@EnableScheduling
public class SchedulingConfig {
    
    public SchedulingConfig() {
        log.info("스케줄링 설정 초기화 완료");
    }
}





