package com.example.insu.service;

import com.example.insu.dto.LearnedPattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 학습된 패턴 품질 스코어링 서비스
 * 
 * 학습 패턴의 품질을 다양한 지표로 평가하여
 * 부정확한 패턴의 적용을 방지하고 고품질 패턴을 우선 사용
 */
@Slf4j
@Service
public class LearnedPatternScoringService {
    
    /**
     * 학습 패턴 품질 점수 계산
     * 
     * @param pattern 학습된 패턴
     * @return 품질 점수 (0-100)
     */
    public int calculatePatternScore(LearnedPattern pattern) {
        int score = pattern.getConfidenceScore(); // 기본 점수 (80)
        
        log.debug("패턴 스코어링 시작: {} (기본 점수: {})", 
                 pattern.getPatternId(), score);
        
        // 1. 적용 성공률 반영 (+20점)
        score += calculateSuccessRateBonus(pattern);
        
        // 2. 최근성 반영 (+10점)
        score += calculateRecencyBonus(pattern);
        
        // 3. 사용 빈도 반영 (+5점)
        score += calculateFrequencyBonus(pattern);
        
        // 4. 우선순위 반영 (+5점)
        score += calculatePriorityBonus(pattern);
        
        // 5. 패턴 복잡도 페널티 (-10점)
        score -= calculateComplexityPenalty(pattern);
        
        int finalScore = Math.max(0, Math.min(score, 100)); // 0-100 범위로 제한
        
        log.debug("패턴 스코어링 완료: {} -> {} 점", 
                 pattern.getPatternId(), finalScore);
        
        return finalScore;
    }
    
    /**
     * 적용 성공률 보너스 계산 (최대 +20점)
     */
    private int calculateSuccessRateBonus(LearnedPattern pattern) {
        if (pattern.getApplyCount() == null || pattern.getApplyCount() == 0) {
            return 0; // 적용 이력 없음
        }
        
        int successCount = pattern.getSuccessCount() != null ? 
                          pattern.getSuccessCount() : 0;
        double successRate = (double) successCount / pattern.getApplyCount();
        
        int bonus = (int) (successRate * 20); // 100% 성공률 시 +20점
        
        log.trace("성공률 보너스: {}% -> +{} 점", 
                 (int)(successRate * 100), bonus);
        
        return bonus;
    }
    
    /**
     * 최근성 보너스 계산 (최대 +10점)
     */
    private int calculateRecencyBonus(LearnedPattern pattern) {
        if (pattern.getUpdatedAt() == null) {
            return 0;
        }
        
        long daysSinceUpdate = ChronoUnit.DAYS.between(
            pattern.getUpdatedAt().toLocalDate(), 
            LocalDate.now()
        );
        
        int bonus = 0;
        if (daysSinceUpdate < 1) {
            bonus = 10; // 오늘 업데이트: +10점
        } else if (daysSinceUpdate < 7) {
            bonus = 7; // 1주일 이내: +7점
        } else if (daysSinceUpdate < 30) {
            bonus = 3; // 1개월 이내: +3점
        }
        
        log.trace("최근성 보너스: {}일 전 -> +{} 점", daysSinceUpdate, bonus);
        
        return bonus;
    }
    
    /**
     * 사용 빈도 보너스 계산 (최대 +5점)
     */
    private int calculateFrequencyBonus(LearnedPattern pattern) {
        if (pattern.getApplyCount() == null) {
            return 0;
        }
        
        int applyCount = pattern.getApplyCount();
        int bonus = 0;
        
        if (applyCount >= 50) {
            bonus = 5; // 매우 자주 사용: +5점
        } else if (applyCount >= 20) {
            bonus = 3; // 자주 사용: +3점
        } else if (applyCount >= 10) {
            bonus = 1; // 가끔 사용: +1점
        }
        
        log.trace("빈도 보너스: {} 회 -> +{} 점", applyCount, bonus);
        
        return bonus;
    }
    
    /**
     * 우선순위 보너스 계산 (최대 +5점)
     */
    private int calculatePriorityBonus(LearnedPattern pattern) {
        if (pattern.getPriority() == null) {
            return 0;
        }
        
        int priority = pattern.getPriority();
        int bonus = 0;
        
        if (priority >= 80) {
            bonus = 5; // 최우선: +5점
        } else if (priority >= 60) {
            bonus = 3; // 높은 우선순위: +3점
        } else if (priority >= 40) {
            bonus = 1; // 중간 우선순위: +1점
        }
        
        log.trace("우선순위 보너스: {} -> +{} 점", priority, bonus);
        
        return bonus;
    }
    
    /**
     * 패턴 복잡도 페널티 계산 (최대 -10점)
     */
    private int calculateComplexityPenalty(LearnedPattern pattern) {
        if (pattern.getPatternValue() == null) {
            return 0;
        }
        
        String value = pattern.getPatternValue();
        int penalty = 0;
        
        // 길이 기반 페널티
        if (value.length() > 200) {
            penalty += 5; // 매우 복잡: -5점
        } else if (value.length() > 100) {
            penalty += 3; // 복잡: -3점
        }
        
        // 특수 패턴 페널티
        if (value.contains("종신:") || value.contains("세만기:")) {
            penalty += 3; // 복합 패턴: -3점
        }
        
        // 특수 문자 과다 페널티
        long specialCharCount = value.chars()
            .filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))
            .count();
        
        if (specialCharCount > 20) {
            penalty += 2; // 특수 문자 과다: -2점
        }
        
        log.trace("복잡도 페널티: 길이={}, 특수문자={} -> -{} 점", 
                 value.length(), specialCharCount, penalty);
        
        return penalty;
    }
    
    /**
     * 패턴 품질 등급 결정
     */
    public String getQualityGrade(int score) {
        if (score >= 90) return "S (매우 우수)";
        if (score >= 80) return "A (우수)";
        if (score >= 70) return "B (양호)";
        if (score >= 60) return "C (보통)";
        if (score >= 50) return "D (주의)";
        return "F (부적격)";
    }
    
    /**
     * 패턴 적용 가능 여부 판단
     */
    public boolean isApplicable(LearnedPattern pattern) {
        int score = calculatePatternScore(pattern);
        
        // 60점 이상만 적용 가능
        boolean applicable = score >= 60;
        
        if (!applicable) {
            log.warn("패턴 품질 부족으로 적용 불가: {} (점수: {}, 등급: {})",
                    pattern.getPatternId(), score, getQualityGrade(score));
        }
        
        return applicable;
    }
}





