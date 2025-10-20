package com.example.insu.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 캐시 메트릭 수집기
 * 캐시 히트율, 메모리 사용량 등을 주기적으로 로깅
 */
@Slf4j
@Component
public class CacheMetricsCollector {
    
    private final CacheManager cacheManager;
    
    public CacheMetricsCollector(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
    
    /**
     * 1분마다 캐시 통계 로깅
     */
    @Scheduled(fixedRate = 60000)
    public void logCacheMetrics() {
        org.springframework.cache.Cache springCache = cacheManager.getCache("parsingCache");
        if (springCache == null) {
            return;
        }
        
        CaffeineCache caffeineCache = (CaffeineCache) springCache;
        Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
        
        // Caffeine 통계
        CacheStats stats = nativeCache.stats();
        
        double hitRate = stats.hitRate() * 100.0;
        double missRate = stats.missRate() * 100.0;
        long hitCount = stats.hitCount();
        long missCount = stats.missCount();
        long evictionCount = stats.evictionCount();
        long size = nativeCache.estimatedSize();
        
        log.info("=== 캐시 통계 ===");
        log.info("캐시 크기: {}/1000", size);
        log.info("히트율: {:.2f}% (히트: {}, 미스: {})", hitRate, hitCount, missCount);
        log.info("미스율: {:.2f}%", missRate);
        log.info("제거 횟수: {}", evictionCount);
        log.info("평균 로드 시간: {:.2f}ms", stats.averageLoadPenalty() / 1_000_000.0);
        log.info("================");
        
        // 경고: 캐시 히트율이 낮으면
        if (hitRate < 50.0 && hitCount + missCount > 100) {
            log.warn("⚠️ 캐시 히트율이 낮습니다 ({}%). 캐시 설정을 검토하세요.", hitRate);
        }
        
        // 경고: 캐시가 거의 가득 찼으면
        if (size > 900) {
            log.warn("⚠️ 캐시가 거의 가득 찼습니다 ({}/1000). 크기 증가를 고려하세요.", size);
        }
    }
    
    /**
     * 캐시 통계 조회 (API용)
     */
    public CacheMetrics getMetrics() {
        org.springframework.cache.Cache springCache = cacheManager.getCache("parsingCache");
        if (springCache == null) {
            return new CacheMetrics(0, 0, 0, 0.0, 0.0, 0);
        }
        
        CaffeineCache caffeineCache = (CaffeineCache) springCache;
        Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
        CacheStats stats = nativeCache.stats();
        
        return new CacheMetrics(
            nativeCache.estimatedSize(),
            stats.hitCount(),
            stats.missCount(),
            stats.hitRate() * 100.0,
            stats.missRate() * 100.0,
            stats.evictionCount()
        );
    }
    
    /**
     * 캐시 메트릭 DTO
     */
    public static class CacheMetrics {
        private final long size;
        private final long hitCount;
        private final long missCount;
        private final double hitRate;
        private final double missRate;
        private final long evictionCount;
        
        public CacheMetrics(long size, long hitCount, long missCount,
                          double hitRate, double missRate, long evictionCount) {
            this.size = size;
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.hitRate = hitRate;
            this.missRate = missRate;
            this.evictionCount = evictionCount;
        }
        
        public long getSize() { return size; }
        public long getHitCount() { return hitCount; }
        public long getMissCount() { return missCount; }
        public double getHitRate() { return hitRate; }
        public double getMissRate() { return missRate; }
        public long getEvictionCount() { return evictionCount; }
        
        @Override
        public String toString() {
            return String.format(
                "CacheMetrics{size=%d, hits=%d, misses=%d, hitRate=%.2f%%, evictions=%d}",
                size, hitCount, missCount, hitRate, evictionCount
            );
        }
    }
}








