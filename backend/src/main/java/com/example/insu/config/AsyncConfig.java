package com.example.insu.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리 설정
 * 
 * - 파싱 작업용 스레드 풀
 * - 학습 작업용 스레드 풀
 * - 배치 작업용 스레드 풀
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {
    
    /**
     * 파싱 작업용 Executor
     * - 코어 풀: 4개 (동시 파싱 처리)
     * - 최대 풀: 10개 (피크 시간 대응)
     * - 큐 용량: 50개 (대기 작업)
     */
    @Bean(name = "parsingExecutor")
    public Executor parsingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("parsing-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        
        log.info("파싱 Executor 초기화 완료: 코어={}, 최대={}, 큐={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());
        
        return executor;
    }
    
    /**
     * 학습 작업용 Executor
     * - 코어 풀: 2개 (학습은 상대적으로 적음)
     * - 최대 풀: 5개
     * - 큐 용량: 100개 (배치 학습 대비)
     */
    @Bean(name = "learningExecutor")
    public Executor learningExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("learning-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        
        log.info("학습 Executor 초기화 완료: 코어={}, 최대={}, 큐={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());
        
        return executor;
    }
    
    /**
     * 배치 작업용 Executor
     * - 코어 풀: 1개 (배치는 단일 스레드로 충분)
     * - 최대 풀: 2개
     * - 큐 용량: 10개
     */
    @Bean(name = "batchExecutor")
    public Executor batchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("batch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(300); // 배치는 시간이 오래 걸릴 수 있음
        executor.initialize();
        
        log.info("배치 Executor 초기화 완료: 코어={}, 최대={}, 큐={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());
        
        return executor;
    }
}





