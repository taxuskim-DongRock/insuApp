package com.example.insu.api;

import com.example.insu.dto.LearningStatistics;
import com.example.insu.web.LearningController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Learning Controller API 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
class LearningControllerApiTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    @DisplayName("POST /api/learning/correction - 수정사항 제출")
    @Transactional
    void testSubmitCorrection() throws Exception {
        // Given
        Map<String, Object> original = new HashMap<>();
        original.put("insuTerm", "종신");
        original.put("payTerm", "10년납");
        original.put("ageRange", "15~80");
        original.put("renew", "비갱신형");
        
        Map<String, Object> corrected = new HashMap<>();
        corrected.put("insuTerm", "종신");
        corrected.put("payTerm", "10년납, 15년납, 20년납");
        corrected.put("ageRange", "남:15~80, 여:15~80");
        corrected.put("renew", "비갱신형");
        
        Map<String, Object> request = new HashMap<>();
        request.put("insuCd", "TEST_API");
        request.put("originalResult", original);
        request.put("correctedResult", corrected);
        request.put("pdfText", "API 테스트 PDF 텍스트");
        
        String jsonRequest = objectMapper.writeValueAsString(request);
        
        // When & Then
        mockMvc.perform(post("/api/learning/correction")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("학습")));
        
        System.out.println("✓ 수정사항 제출 API 테스트 통과");
    }
    
    @Test
    @DisplayName("GET /api/learning/statistics - 통계 조회")
    void testGetStatistics() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/learning/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCorrections", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.totalPatterns", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.currentAccuracy", greaterThanOrEqualTo(0.0)));
        
        System.out.println("✓ 통계 조회 API 테스트 통과");
    }
    
    @Test
    @DisplayName("POST /api/learning/reset - 학습 데이터 초기화")
    @Transactional
    void testResetLearning() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/learning/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("초기화")));
        
        System.out.println("✓ 학습 데이터 초기화 API 테스트 통과");
    }
    
    @Test
    @DisplayName("POST /api/learning/correction - 잘못된 요청")
    void testSubmitCorrectionWithInvalidRequest() throws Exception {
        // Given: 필수 필드 누락
        Map<String, Object> invalidRequest = new HashMap<>();
        invalidRequest.put("insuCd", "TEST_INVALID");
        // originalResult, correctedResult 누락
        
        String jsonRequest = objectMapper.writeValueAsString(invalidRequest);
        
        // When & Then: 400 에러 또는 처리 가능
        mockMvc.perform(post("/api/learning/correction")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().is4xxClientError());
        
        System.out.println("✓ 잘못된 요청 처리 테스트 통과");
    }
    
    @Test
    @DisplayName("API 전체 플로우 테스트")
    @Transactional
    void testCompleteFlow() throws Exception {
        // 1. 수정사항 제출
        Map<String, Object> original = new HashMap<>();
        original.put("insuTerm", "종신");
        original.put("payTerm", "10년납");
        original.put("ageRange", "15~80");
        original.put("renew", "비갱신형");
        
        Map<String, Object> corrected = new HashMap<>();
        corrected.put("insuTerm", "종신");
        corrected.put("payTerm", "10년납, 15년납");
        corrected.put("ageRange", "남:15~80, 여:15~80");
        corrected.put("renew", "비갱신형");
        
        Map<String, Object> request = new HashMap<>();
        request.put("insuCd", "FLOW_TEST");
        request.put("originalResult", original);
        request.put("correctedResult", corrected);
        request.put("pdfText", "플로우 테스트 PDF");
        
        String jsonRequest = objectMapper.writeValueAsString(request);
        
        mockMvc.perform(post("/api/learning/correction")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
        
        // 2. 통계 확인
        mockMvc.perform(get("/api/learning/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCorrections", greaterThan(0)));
        
        System.out.println("✓ 전체 플로우 테스트 통과");
    }
}






