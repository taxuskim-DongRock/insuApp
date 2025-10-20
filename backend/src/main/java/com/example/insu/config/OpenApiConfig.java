package com.example.insu.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger 설정
 * 
 * API 문서 자동 생성:
 * - Swagger UI: http://localhost:8081/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8081/v3/api-docs
 */
@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("보험 상품 파싱 및 학습 시스템 API")
                .version("2.0.0")
                .description("""
                    # 보험 상품 PDF 파싱 및 증분 학습 시스템
                    
                    ## 주요 기능
                    
                    ### 1. PDF 파싱
                    - **하이브리드 전략**: 5가지 파싱 전략을 우선순위별로 실행
                    - **CSV Few-Shot**: 23개 검증된 CSV 파일 기반 Few-Shot 학습
                    - **Caffeine 캐시**: 24시간 TTL, 최대 1000개 캐싱
                    
                    ### 2. 증분 학습
                    - **사용자 수정 학습**: 실시간 패턴 학습 및 적용
                    - **품질 스코어링**: 패턴 품질 자동 평가 (0-100점)
                    - **배치 학습**: 매일 새벽 2시 자동 실행
                    
                    ### 3. 복잡 패턴 보정
                    - **자동 패턴 추출**: 복잡한 가입나이 → 정상 패턴
                    - **학습 패턴 우선**: 사용자 수정 패턴 최우선 적용
                    - **CSV 패턴 참조**: UW_CODE_MAPPING 데이터 활용
                    
                    ### 4. 성능 최적화
                    - **비동기 처리**: 여러 상품 동시 파싱
                    - **캐시 워밍업**: 시작 시 자주 사용되는 상품 사전 로드
                    - **메트릭 수집**: 실시간 성능 모니터링
                    
                    ## 파싱 전략 우선순위
                    
                    1. **UwMappingValidatedParsingStrategy** (신뢰도 95%)
                       - UW_CODE_MAPPING + 학습 패턴 + 패턴 보정
                    2. **UwMappingParsingStrategy** (신뢰도 90%)
                       - UW_CODE_MAPPING 직접 조회
                    3. **LlmParsingStrategy** (신뢰도 70%)
                       - Ollama LLM 기본 파싱
                    4. **FewShotLlmParsingStrategy** (신뢰도 75-85%)
                       - CSV Few-Shot + Quorum LLM
                    5. **AdvancedLlmParsingStrategy** (신뢰도 80%)
                       - 고급 LLM 파싱
                    
                    ## 데이터 소스
                    
                    - **PDF 파일**: `C:/insu_app/insuPdf`
                    - **CSV 파일**: `C:/insu_app/insuCsv` (23개 문서)
                    - **학습 데이터**: Oracle DB (LEARNED_PATTERN, CORRECTION_LOG)
                    """)
                .contact(new Contact()
                    .name("Insurance System Team")
                    .email("support@example.com"))
                .license(new License()
                    .name("Internal Use Only")
                    .url("https://example.com/license")))
            .addServersItem(new Server()
                .url("http://localhost:8081")
                .description("로컬 개발 서버"))
            .addServersItem(new Server()
                .url("http://localhost:8080")
                .description("로컬 개발 서버 (대체 포트)"));
    }
}





