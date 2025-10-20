package com.example.insu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Phase 3: 사용자 수정 로그 DTO (DB 연동 버전)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrectionLog {
    
    // 기본 정보
    private Long id;
    private String insuCd;
    private String srcFile;
    private String productName;
    
    // 원본 파싱 결과 (개별 필드)
    private String originalInsuTerm;
    private String originalPayTerm;
    private String originalAgeRange;
    private String originalRenew;
    private String originalSpecialNotes;
    private String originalValidationSource;
    
    // 사용자 수정 결과 (개별 필드)
    private String correctedInsuTerm;
    private String correctedPayTerm;
    private String correctedAgeRange;
    private String correctedRenew;
    private String correctedSpecialNotes;
    
    // 수정 메타데이터
    private String pdfText;
    private Integer correctedFieldCount;
    private String correctionReason;
    private String userId;
    private LocalDateTime timestamp;
    
    // 학습 상태
    private Character isLearned;
    private LocalDateTime learnedAt;
    private Long learningPatternId;
    
    // 검증 정보
    private Integer validationScore;
    private Character isVerified;
    private String verifiedBy;
    private LocalDateTime verifiedAt;
    
    // 하위 호환성을 위한 Map 변환 (기존 코드 호환)
    private Map<String, String> originalResult;
    private Map<String, String> correctedResult;
    
    /**
     * 수정 이유 (기존 필드명 호환)
     */
    public String getReason() {
        return correctionReason;
    }
    
    public void setReason(String reason) {
        this.correctionReason = reason;
    }
}



