package com.example.insu.aspect;

import com.example.insu.service.ParsingMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 파싱 로깅 AOP
 * 
 * 모든 파싱 전략의 실행을 자동으로 로깅하고
 * 성능 메트릭을 수집
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ParsingLoggingAspect {
    
    @Autowired(required = false)
    private ParsingMetricsService metricsService;
    
    /**
     * 모든 ParsingStrategy.parse() 메서드 실행 시 로깅
     */
    @Around("execution(* com.example.insu.service.*ParsingStrategy.parse(..))")
    public Object logParsing(ProceedingJoinPoint joinPoint) throws Throwable {
        String strategyName = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();
        String insuCd = args.length > 1 ? String.valueOf(args[1]) : "unknown";
        
        log.info("╔═══════════════════════════════════════════════════════╗");
        log.info("║  ▶ 파싱 시작: {:30s}              ║", strategyName);
        log.info("║    상품코드: {:10s}                                   ║", insuCd);
        log.info("╚═══════════════════════════════════════════════════════╝");
        
        long startTime = System.currentTimeMillis();
        Object result = null;
        Exception error = null;
        
        try {
            result = joinPoint.proceed();
            return result;
            
        } catch (Exception e) {
            error = e;
            throw e;
            
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            boolean success = (error == null && isValidResult(result));
            
            // 메트릭 기록
            if (metricsService != null) {
                metricsService.recordParsing(strategyName, duration, success);
            }
            
            // 로그 출력
            if (success) {
                log.info("╔═══════════════════════════════════════════════════════╗");
                log.info("║  ✓ 파싱 완료: {:30s}              ║", strategyName);
                log.info("║    상품코드: {:10s}                                   ║", insuCd);
                log.info("║    처리 시간: {} ms                                   ║", duration);
                log.info("║    결과: {} 개 필드                                   ║", 
                        result instanceof Map ? ((Map<?, ?>) result).size() : 0);
                log.info("╚═══════════════════════════════════════════════════════╝");
            } else {
                log.error("╔═══════════════════════════════════════════════════════╗");
                log.error("║  ✗ 파싱 실패: {:30s}              ║", strategyName);
                log.error("║    상품코드: {:10s}                                   ║", insuCd);
                log.error("║    처리 시간: {} ms                                   ║", duration);
                log.error("║    오류: {}                                           ║", 
                         error != null ? error.getMessage() : "결과 없음");
                log.error("╚═══════════════════════════════════════════════════════╝");
            }
        }
    }
    
    /**
     * 학습 서비스 메서드 로깅
     */
    @Around("execution(* com.example.insu.service.IncrementalLearningService.logCorrection(..))")
    public Object logLearning(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String insuCd = args.length > 0 ? String.valueOf(args[0]) : "unknown";
        
        log.info("╔═══════════════════════════════════════════════════════╗");
        log.info("║  📚 학습 시작: {:10s}                                 ║", insuCd);
        log.info("╚═══════════════════════════════════════════════════════╝");
        
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("╔═══════════════════════════════════════════════════════╗");
            log.info("║  ✓ 학습 완료: {:10s}                                 ║", insuCd);
            log.info("║    처리 시간: {} ms                                   ║", duration);
            log.info("╚═══════════════════════════════════════════════════════╝");
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            
            log.error("╔═══════════════════════════════════════════════════════╗");
            log.error("║  ✗ 학습 실패: {:10s}                                 ║", insuCd);
            log.error("║    처리 시간: {} ms                                   ║", duration);
            log.error("║    오류: {}                                           ║", e.getMessage());
            log.error("╚═══════════════════════════════════════════════════════╝");
            
            throw e;
        }
    }
    
    /**
     * 파싱 결과 유효성 검증
     */
    private boolean isValidResult(Object result) {
        if (!(result instanceof Map)) {
            return false;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>) result;
        
        // 최소 1개 필드가 유효한 값을 가져야 함
        return map.values().stream()
            .anyMatch(v -> v != null && !v.isEmpty() && !v.equals("—"));
    }
}





