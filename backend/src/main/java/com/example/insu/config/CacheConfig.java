package com.example.insu.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 개선된 캐시 설정 (Caffeine)
 * 문제 해결: 무제한 성장, TTL 부재, 메모리 누수
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("parsingCache");
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }
    
    /**
     * Caffeine 캐시 빌더
     * - 최대 1000개 엔트리 (메모리 보호)
     * - 24시간 TTL (자동 만료)
     * - 6시간 idle 후 제거 (슬라이딩 만료)
     * - 통계 수집 (히트율 모니터링)
     */
    @Bean
    public Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
            .maximumSize(1000)  // 최대 1000개
            .expireAfterWrite(24, TimeUnit.HOURS)  // 24시간 후 만료
            .expireAfterAccess(6, TimeUnit.HOURS)  // 6시간 미사용 시 제거
            .recordStats()  // 통계 수집
            .removalListener((Object key, Object value, RemovalCause cause) -> {
                log.debug("캐시 제거: key={}, cause={}", key, cause);
            });
    }
    
}

