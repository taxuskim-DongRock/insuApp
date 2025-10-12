# 아키텍처 개선 계획서 - 문제점 분석 및 해결 방안

## 📋 목차
1. [문제점 1: 인메모리 캐시 설계의 한계](#문제점-1-인메모리-캐시-설계의-한계)
2. [문제점 2: LLM 병렬 처리 성능/안정성 리스크](#문제점-2-llm-병렬-처리-성능안정성-리스크)
3. [문제점 3: 신뢰도 점수 휴리스틱 의존](#문제점-3-신뢰도-점수-휴리스틱-의존)
4. [문제점 4: 점진 학습 거버넌스/영속성 미흡](#문제점-4-점진-학습-거버넌스영속성-미흡)
5. [문제점 5: 정규식 파싱 취약성](#문제점-5-정규식-파싱-취약성)
6. [우선순위별 구현 로드맵](#우선순위별-구현-로드맵)

---

## 🔴 문제점 1: 인메모리 캐시 설계의 한계

### 현재 구현 분석

```java
// HybridParsingService.java (라인 19-23)
private final Map<String, Map<String, String>> resultCache;

public HybridParsingService(List<ParsingStrategy> strategies) {
    this.strategies = strategies;
    this.resultCache = new HashMap<>();  // ❌ 문제: 단순 HashMap
    // ...
}

// 캐시 키 생성 (라인 149-151)
private String generateCacheKey(File pdfFile, String insuCd) {
    return pdfFile.getName() + "_" + insuCd;  // ❌ 문제: PDF 내용 변경 감지 불가
}
```

### 문제점 상세 분석

#### 1.1 확인된 문제
✅ **분석 결과: 제시된 문제 적합**

- **무제한 성장**: `HashMap`은 크기 제한 없음 → 메모리 누수 위험
- **TTL 부재**: 한번 캐싱되면 서버 재시작까지 유지
- **분산 불가**: 단일 JVM 메모리 → 스케일아웃 시 캐시 불일치
- **버전 관리 부재**: 파서 로직 변경 시 무효화 불가
- **동시성 제어 미흡**: `HashMap`은 thread-unsafe (ConcurrentHashMap 권장)

#### 1.2 영향도 평가
- **심각도**: 🔴 HIGH
- **발생 가능성**: 높음 (장시간 운영 시 메모리 누적)
- **영향 범위**: 성능 저하, 메모리 부족, 스케일아웃 불가

### 개선 방안

#### 방안 1-A: Caffeine Cache 도입 (단기 - 우선순위 1)

```java
// build.gradle 의존성 추가
dependencies {
    implementation 'com.github.ben-manes.caffeine:caffeine:3.1.8'
    implementation 'org.springframework.boot:spring-boot-starter-cache'
}

// CacheConfig.java (신규)
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("parsingCache");
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }
    
    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
            .maximumSize(1000)  // 최대 1000개 엔트리
            .expireAfterWrite(24, TimeUnit.HOURS)  // 24시간 TTL
            .expireAfterAccess(6, TimeUnit.HOURS)  // 6시간 idle 후 제거
            .recordStats()  // 통계 수집
            .removalListener((key, value, cause) -> {
                log.info("캐시 제거: key={}, cause={}", key, cause);
            });
    }
    
    @Bean
    public CacheMetrics cacheMetrics(CacheManager cacheManager, MeterRegistry registry) {
        // Micrometer로 캐시 메트릭 노출
        return new CacheMetrics(cacheManager, registry);
    }
}

// HybridParsingService.java 수정
@Service
@Slf4j
public class HybridParsingService {
    
    private final CacheManager cacheManager;
    private final String parserVersion = "1.0.0";  // 배포 시 변경
    
    @Cacheable(value = "parsingCache", key = "#cacheKey")
    public Map<String, String> parseWithMultipleStrategies(File pdfFile, String insuCd) {
        String cacheKey = generateCacheKey(pdfFile, insuCd);
        // ... 파싱 로직
    }
    
    private String generateCacheKey(File pdfFile, String insuCd) {
        // PDF 해시 + 보험코드 + 파서 버전
        String pdfHash = calculateFileHash(pdfFile);
        return String.format("%s_%s_%s", pdfHash, insuCd, parserVersion);
    }
    
    private String calculateFileHash(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }
            return Base64.getEncoder().encodeToString(md.digest());
        } catch (Exception e) {
            log.warn("파일 해시 계산 실패, 파일명 사용: {}", file.getName());
            return file.getName();
        }
    }
}
```

**장점:**
- ✅ 크기/TTL 제한으로 메모리 보호
- ✅ 통계 수집으로 히트율 모니터링
- ✅ 파일 해시로 내용 변경 감지
- ✅ 파서 버전으로 안전한 무효화

**단점:**
- ⚠️ 여전히 단일 노드 한정 (스케일아웃 불가)

#### 방안 1-B: Redis 분산 캐시 추가 (중기 - 우선순위 2)

```java
// build.gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.redisson:redisson-spring-boot-starter:3.24.3'
}

// RedisConfig.java
@Configuration
public class RedisConfig {
    
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
            .setAddress("redis://localhost:6379")
            .setConnectionPoolSize(64)
            .setConnectionMinimumIdleSize(10)
            .setTimeout(10000)
            .setRetryAttempts(3);
        
        return Redisson.create(config);
    }
    
    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(24))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }
}

// TwoLevelCacheService.java (신규 - L1: Caffeine, L2: Redis)
@Service
@Slf4j
public class TwoLevelCacheService {
    
    private final Cache caffeineCache;  // L1: 로컬 캐시
    private final RedissonClient redisClient;  // L2: 분산 캐시
    private static final String REDIS_PREFIX = "parsing:";
    
    public Optional<Map<String, String>> get(String key) {
        // L1 캐시 확인
        Map<String, String> result = caffeineCache.getIfPresent(key);
        if (result != null) {
            log.debug("L1 캐시 히트: {}", key);
            return Optional.of(result);
        }
        
        // L2 캐시 확인
        RBucket<Map<String, String>> bucket = redisClient.getBucket(REDIS_PREFIX + key);
        result = bucket.get();
        if (result != null) {
            log.debug("L2 캐시 히트: {}", key);
            // L1에 역채움
            caffeineCache.put(key, result);
            return Optional.of(result);
        }
        
        log.debug("캐시 미스: {}", key);
        return Optional.empty();
    }
    
    public void put(String key, Map<String, String> value) {
        // L1 & L2 동시 저장
        caffeineCache.put(key, value);
        RBucket<Map<String, String>> bucket = redisClient.getBucket(REDIS_PREFIX + key);
        bucket.set(value, 24, TimeUnit.HOURS);
        log.debug("캐시 저장 (L1+L2): {}", key);
    }
    
    public void invalidateByVersion(String oldVersion, String newVersion) {
        // 버전 기반 무효화
        RKeys keys = redisClient.getKeys();
        keys.getKeysByPattern(REDIS_PREFIX + "*_" + oldVersion)
            .forEach(key -> {
                redisClient.getBucket(key).delete();
                log.info("구버전 캐시 삭제: {}", key);
            });
    }
}
```

**장점:**
- ✅ 스케일아웃 지원 (여러 서버가 캐시 공유)
- ✅ L1(로컬) + L2(분산) 2계층으로 최적 성능
- ✅ 버전 기반 일괄 무효화

**구현 가능성: ✅ 가능**
- 기존 코드 수정 최소 (Cacheable 애노테이션만 변경)
- Redis 설치 필요 (Docker 사용 시 간단)

---

## 🔴 문제점 2: LLM 병렬 처리 성능/안정성 리스크

### 현재 구현 분석

```java
// FewShotLlmParsingStrategy.java (라인 54-64)
CompletableFuture<Map<String, String>> llamaFuture = 
    ollamaService.parseWithLlama(prompt, insuCd);
CompletableFuture<Map<String, String>> mistralFuture = 
    ollamaService.parseWithMistral(prompt, insuCd);
CompletableFuture<Map<String, String>> codeLlamaFuture = 
    ollamaService.parseWithCodeLlama(prompt, insuCd);

// 모든 LLM 완료 대기 (타임아웃: 30초)
CompletableFuture.allOf(llamaFuture, mistralFuture, codeLlamaFuture)
    .get(30, TimeUnit.SECONDS);  // ❌ 문제: 가장 느린 모델이 전체 시간 결정
```

### 문제점 상세 분석

#### 2.1 확인된 문제
✅ **분석 결과: 제시된 문제 매우 적합**

- **All-or-Nothing**: 1개 모델 지연 → 전체 지연 (최악의 경우 3개 모두 30초 대기)
- **서킷브레이커 부재**: 특정 모델 장애 시 반복 실패
- **부분 성공 미처리**: 2개 모델 성공해도 1개 실패 시 전체 실패
- **스레드 풀 고갈 위험**: 동시 요청 시 블로킹
- **동적 타임아웃 부재**: 모델 특성 무시 (Mistral은 빠르지만 Llama는 느릴 수 있음)

#### 2.2 영향도 평가
- **심각도**: 🔴 HIGH
- **발생 가능성**: 높음 (LLM 응답 시간 변동성 큼)
- **영향 범위**: 사용자 경험 저하, 시스템 과부하

### 개선 방안

#### 방안 2-A: 쿼럼 기반 조기 종료 (단기 - 우선순위 1)

```java
// QuorumLlmService.java (신규)
@Service
@Slf4j
public class QuorumLlmService {
    
    private final OllamaService ollamaService;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    
    /**
     * 쿼럼 기반 파싱: 2/3 일치 시 조기 종료
     */
    public Map<String, String> parseWithQuorum(String prompt, String insuCd) {
        log.info("쿼럼 기반 LLM 파싱 시작: {}", insuCd);
        
        // 3개 모델 병렬 실행
        List<CompletableFuture<ModelResult>> futures = List.of(
            CompletableFuture.supplyAsync(() -> 
                callModel("Llama", () -> ollamaService.parseWithLlama(prompt, insuCd)), executor),
            CompletableFuture.supplyAsync(() -> 
                callModel("Mistral", () -> ollamaService.parseWithMistral(prompt, insuCd)), executor),
            CompletableFuture.supplyAsync(() -> 
                callModel("CodeLlama", () -> ollamaService.parseWithCodeLlama(prompt, insuCd)), executor)
        );
        
        // 결과 수집 (최대 30초, 하지만 쿼럼 달성 시 조기 종료)
        List<ModelResult> results = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        long deadline = startTime + 30000;
        
        for (CompletableFuture<ModelResult> future : futures) {
            try {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining > 0) {
                    ModelResult result = future.get(remaining, TimeUnit.MILLISECONDS);
                    results.add(result);
                    
                    // 쿼럼 확인: 2개 이상 일치 시 조기 종료
                    if (results.size() >= 2 && hasQuorum(results)) {
                        log.info("쿼럼 달성 (2/3), 조기 종료");
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.info("총 소요 시간: {}ms (30초 대신)", elapsed);
                        
                        // 나머지 요청 취소
                        futures.forEach(f -> f.cancel(true));
                        break;
                    }
                }
            } catch (TimeoutException e) {
                log.warn("모델 타임아웃");
            } catch (Exception e) {
                log.error("모델 실행 오류: {}", e.getMessage());
            }
        }
        
        // 결과 통합
        return integrateResultsWithQuorum(results);
    }
    
    private ModelResult callModel(String modelName, 
                                  Supplier<CompletableFuture<Map<String, String>>> supplier) {
        long start = System.currentTimeMillis();
        try {
            Map<String, String> result = supplier.get().get(10, TimeUnit.SECONDS);  // 모델별 10초 타임아웃
            long elapsed = System.currentTimeMillis() - start;
            return new ModelResult(modelName, result, true, elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[{}] 실패: {} ({}ms)", modelName, e.getMessage(), elapsed);
            return new ModelResult(modelName, null, false, elapsed);
        }
    }
    
    private boolean hasQuorum(List<ModelResult> results) {
        if (results.size() < 2) return false;
        
        // 2개 이상의 모델이 동일한 insuTerm, payTerm을 반환하는지 확인
        Map<String, Long> insuTermCounts = results.stream()
            .filter(r -> r.isSuccess() && r.getResult() != null)
            .map(r -> r.getResult().get("insuTerm"))
            .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        
        return insuTermCounts.values().stream().anyMatch(count -> count >= 2);
    }
    
    private Map<String, String> integrateResultsWithQuorum(List<ModelResult> results) {
        // 투표 기반 통합 (다수결)
        Map<String, String> integrated = new HashMap<>();
        
        for (String field : List.of("insuTerm", "payTerm", "ageRange", "renew")) {
            Map<String, Long> votes = results.stream()
                .filter(r -> r.isSuccess() && r.getResult() != null)
                .map(r -> r.getResult().get(field))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
            
            // 최다 득표 값 선택
            String winner = votes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("—");
            
            integrated.put(field, winner);
        }
        
        integrated.put("specialNotes", 
            String.format("쿼럼 기반 통합 (%d/3 모델 사용)", results.size()));
        
        return integrated;
    }
    
    @Data
    private static class ModelResult {
        private final String modelName;
        private final Map<String, String> result;
        private final boolean success;
        private final long elapsedTime;
    }
}
```

**장점:**
- ✅ 평균 응답 시간 50% 단축 (30초 → 15초 예상)
- ✅ 부분 실패 허용 (2/3 성공 시 OK)
- ✅ 가장 느린 모델이 전체 속도 결정 안 함

#### 방안 2-B: 서킷브레이커 + 헤지드 요청 (중기 - 우선순위 2)

```java
// build.gradle
dependencies {
    implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.1.0'
    implementation 'io.github.resilience4j:resilience4j-circuitbreaker:2.1.0'
}

// CircuitBreakerConfig.java
@Configuration
public class CircuitBreakerConfig {
    
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)  // 50% 실패 시 오픈
            .waitDurationInOpenState(Duration.ofSeconds(60))  // 60초 후 재시도
            .slidingWindowSize(10)  // 최근 10개 요청 기준
            .minimumNumberOfCalls(5)  // 최소 5개 요청 후 판단
            .build();
        
        return CircuitBreakerRegistry.of(config);
    }
    
    @Bean
    public CircuitBreaker llamaCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("llama");
    }
    
    @Bean
    public CircuitBreaker mistralCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("mistral");
    }
    
    @Bean
    public CircuitBreaker codeLlamaCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("codellama");
    }
}

// ResilientLlmService.java (신규)
@Service
@Slf4j
public class ResilientLlmService {
    
    private final CircuitBreaker llamaCircuitBreaker;
    private final CircuitBreaker mistralCircuitBreaker;
    private final CircuitBreaker codeLlamaCircuitBreaker;
    private final OllamaService ollamaService;
    
    public CompletableFuture<Map<String, String>> parseWithLlama(String prompt, String insuCd) {
        return CircuitBreaker.decorateFuture(llamaCircuitBreaker, 
            () -> ollamaService.parseWithLlama(prompt, insuCd));
    }
    
    /**
     * 헤지드 요청: 1초 지연 시 백업 모델 호출
     */
    public Map<String, String> parseWithHedging(String prompt, String insuCd) {
        CompletableFuture<Map<String, String>> primary = 
            parseWithLlama(prompt, insuCd);
        
        // 1초 후 백업 요청 시작
        CompletableFuture<Map<String, String>> hedge = 
            CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
                .execute(() -> parseWithMistral(prompt, insuCd));
        
        // 먼저 완료되는 것 반환
        return CompletableFuture.anyOf(primary, hedge)
            .thenApply(result -> (Map<String, String>) result)
            .get(10, TimeUnit.SECONDS);
    }
}
```

**장점:**
- ✅ 장애 모델 자동 차단 (반복 실패 방지)
- ✅ 헤지드 요청으로 p99 레이턴시 개선
- ✅ 시스템 안정성 향상

**구현 가능성: ✅ 가능**
- Resilience4j 라이브러리 추가만 필요
- 기존 코드와 호환성 높음

---

## 🟡 문제점 3: 신뢰도 점수 휴리스틱 의존

### 현재 구현 분석

```java
// MultiLayerValidationService.java
public ValidationResult validate(Map<String, String> terms, String pdfText, String insuCd) {
    int totalScore = 0;
    
    // Layer 1: 구문 검증 (25점)  // ❌ 문제: 고정 가중치
    int syntaxScore = validateSyntax(terms, failures);
    totalScore += syntaxScore;
    
    // Layer 2: 의미 검증 (25점)  // ❌ 문제: 동등 가중
    int semanticScore = validateSemantics(terms, failures);
    totalScore += semanticScore;
    
    // ... Layer 3, 4도 각 25점
    
    // ❌ 문제: 고정 임계값 85%
    if (confidence >= 85) {
        return result;
    }
}
```

### 문제점 상세 분석

#### 3.1 확인된 문제
✅ **분석 결과: 제시된 문제 적합**

- **동등 가중**: 모든 레이어가 25점씩 (실제로는 필드별 중요도 다름)
- **고정 임계값**: 85%가 적절한지 근거 없음
- **캘리브레이션 부재**: "85점"이 "85% 정확"을 의미하지 않음
- **필드별 차별 없음**: ageRange가 중요하지만 insuTerm과 동등

#### 3.2 영향도 평가
- **심각도**: 🟡 MEDIUM
- **발생 가능성**: 중간 (특정 케이스에서 과신/과거신)
- **영향 범위**: 정확도 개선의 한계

### 개선 방안

#### 방안 3-A: 학습 기반 신뢰도 모델 (장기 - 우선순위 3)

```java
// ConfidenceCalibrationService.java (신규)
@Service
@Slf4j
public class ConfidenceCalibrationService {
    
    private LogisticRegression calibrationModel;  // Platt Scaling 모델
    private Map<String, Double> fieldWeights;  // 필드별 가중치
    
    @PostConstruct
    public void initialize() {
        // 초기 가중치 (도메인 전문가 의견 기반)
        fieldWeights = Map.of(
            "insuTerm", 1.2,   // 보험기간 중요도 높음
            "payTerm", 1.2,    // 납입기간 중요도 높음
            "ageRange", 1.5,   // 가입나이 가장 중요 (계산에 직접 영향)
            "renew", 0.8       // 갱신여부 상대적으로 덜 중요
        );
        
        // 검증 데이터셋으로 캘리브레이션 모델 학습
        trainCalibrationModel();
    }
    
    /**
     * 라벨된 검증셋으로 모델 학습
     */
    private void trainCalibrationModel() {
        // 실제 구현: 100개+ 라벨된 데이터로 학습
        // 피처: [syntaxScore, semanticScore, domainScore, llmScore, 
        //        llmAgreement, regexMatch, dbMatch]
        // 라벨: 실제 정확 여부 (0 또는 1)
        
        log.info("신뢰도 캘리브레이션 모델 학습 완료");
    }
    
    /**
     * 캘리브레이션된 신뢰도 계산
     */
    public CalibratedConfidence calculateConfidence(
            Map<String, String> terms,
            ValidationScores scores,
            Map<String, String> features) {
        
        // 1. 가중 점수 계산
        double weightedScore = calculateWeightedScore(terms, scores);
        
        // 2. 추가 피처 생성
        double llmAgreement = calculateLlmAgreement(terms);
        double regexMatch = calculateRegexMatch(terms);
        double dbMatch = checkDatabaseMatch(terms);
        
        // 3. 로지스틱 회귀로 확률 계산
        double rawScore = weightedScore;
        double calibratedProbability = sigmoid(
            rawScore * 0.01 +  // 기본 점수
            llmAgreement * 0.3 +  // LLM 합의도
            regexMatch * 0.2 +    // 정규식 일치도
            dbMatch * 0.5         // DB 매칭 (가장 강력한 신호)
        );
        
        // 4. 필드별 최소 신뢰도 확인
        Map<String, Double> fieldConfidences = calculateFieldConfidences(terms);
        boolean allFieldsAboveThreshold = fieldConfidences.values().stream()
            .allMatch(conf -> conf >= 0.7);  // 각 필드 최소 70%
        
        return new CalibratedConfidence(
            calibratedProbability,
            weightedScore,
            fieldConfidences,
            allFieldsAboveThreshold
        );
    }
    
    private double calculateWeightedScore(Map<String, String> terms, ValidationScores scores) {
        double totalWeight = 0.0;
        double weightedSum = 0.0;
        
        for (String field : terms.keySet()) {
            double weight = fieldWeights.getOrDefault(field, 1.0);
            double fieldScore = scores.getFieldScore(field);
            
            weightedSum += fieldScore * weight;
            totalWeight += weight * 25.0;  // 각 필드 최대 25점
        }
        
        return (weightedSum / totalWeight) * 100.0;
    }
    
    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }
    
    private double calculateLlmAgreement(Map<String, String> terms) {
        // 3개 모델이 얼마나 일치하는지
        // specialNotes에서 추출 또는 별도 저장
        return 0.8;  // 더미
    }
    
    @Data
    public static class CalibratedConfidence {
        private final double probability;  // 0.0 ~ 1.0 (실제 확률)
        private final double rawScore;     // 0 ~ 100 (기존 점수)
        private final Map<String, Double> fieldConfidences;
        private final boolean allFieldsReliable;
    }
}
```

**장점:**
- ✅ 과학적 근거 기반 신뢰도 (확률로 해석 가능)
- ✅ 필드별 중요도 반영
- ✅ 지속적 개선 (새 데이터로 재학습)

**단점:**
- ⚠️ 라벨된 검증 데이터 필요 (100개+ 수동 검증)
- ⚠️ 모델 학습/관리 오버헤드

**구현 가능성: ⚠️ 조건부 가능**
- 검증 데이터셋 구축 필요 (시간 소요)
- ML 라이브러리 (Smile, DL4J) 또는 Python 연동 필요

---

## 🟡 문제점 4: 점진 학습 거버넌스/영속성 미흡

### 현재 구현 분석

```java
// IncrementalLearningService.java (라인 22-26)
// 사용자 수정 로그
private final List<CorrectionLog> correctionLogs = Collections.synchronizedList(new ArrayList<>());

// 학습된 패턴 (보험코드 + 필드 → 올바른 값)
private final Map<String, String> learnedPatterns = new ConcurrentHashMap<>();
// ❌ 문제: 메모리 전용, 재시작 시 소실
```

### 문제점 상세 분석

#### 4.1 확인된 문제
✅ **분석 결과: 제시된 문제 매우 적합**

- **휘발성**: 서버 재시작 시 학습 데이터 모두 소실
- **승인 프로세스 부재**: 잘못된 수정도 즉시 학습
- **버전 관리 부재**: 패턴 변경 이력 없음
- **롤백 불가**: 잘못된 학습 취소 방법 없음
- **감사 추적 없음**: 누가 언제 수정했는지 기록 없음

#### 4.2 영향도 평가
- **심각도**: 🟡 MEDIUM (운영 환경에서는 HIGH)
- **발생 가능성**: 높음 (실수로 잘못된 수정 가능)
- **영향 범위**: 데이터 품질 저하, 운영 리스크

### 개선 방안

#### 방안 4-A: DB 영속화 + 워크플로 (중기 - 우선순위 2)

```sql
-- schema.sql
CREATE TABLE correction_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    insu_cd VARCHAR(10) NOT NULL,
    field_name VARCHAR(50) NOT NULL,
    original_value TEXT,
    corrected_value TEXT NOT NULL,
    pdf_hash VARCHAR(64),
    status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, APPROVED, REJECTED
    submitted_by VARCHAR(50),
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_by VARCHAR(50),
    reviewed_at TIMESTAMP,
    INDEX idx_insu_cd (insu_cd),
    INDEX idx_status (status)
);

CREATE TABLE learned_pattern (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    pattern_key VARCHAR(100) UNIQUE NOT NULL,  -- insuCd_field
    pattern_value TEXT NOT NULL,
    confidence_score DECIMAL(5,2),
    version INT DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pattern_key (pattern_key),
    INDEX idx_active (is_active)
);

CREATE TABLE few_shot_example (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    example_content TEXT NOT NULL,
    category VARCHAR(50),  -- 주계약, 특약, 갱신형 등
    version INT DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    created_from_correction_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_from_correction_id) REFERENCES correction_log(id)
);
```

```java
// PersistentLearningService.java (신규)
@Service
@Slf4j
@Transactional
public class PersistentLearningService {
    
    private final CorrectionLogRepository correctionLogRepository;
    private final LearnedPatternRepository learnedPatternRepository;
    private final FewShotExampleRepository fewShotExampleRepository;
    
    /**
     * 사용자 수정 제출 (승인 대기)
     */
    public CorrectionLog submitCorrection(String insuCd, 
                                         Map<String, String> original,
                                         Map<String, String> corrected,
                                         String submittedBy) {
        
        CorrectionLog log = new CorrectionLog();
        log.setInsuCd(insuCd);
        log.setStatus(CorrectionStatus.PENDING);
        log.setSubmittedBy(submittedBy);
        log.setSubmittedAt(LocalDateTime.now());
        
        // 필드별 로그 생성
        for (String field : corrected.keySet()) {
            if (!original.get(field).equals(corrected.get(field))) {
                CorrectionLog fieldLog = log.clone();
                fieldLog.setFieldName(field);
                fieldLog.setOriginalValue(original.get(field));
                fieldLog.setCorrectedValue(corrected.get(field));
                correctionLogRepository.save(fieldLog);
            }
        }
        
        log.info("수정사항 제출 완료: {} (승인 대기)", insuCd);
        return log;
    }
    
    /**
     * 수정사항 승인 (관리자)
     */
    public void approveCorrection(Long correctionId, String reviewedBy) {
        CorrectionLog log = correctionLogRepository.findById(correctionId)
            .orElseThrow(() -> new IllegalArgumentException("수정 로그 없음"));
        
        if (log.getStatus() != CorrectionStatus.PENDING) {
            throw new IllegalStateException("승인 대기 상태가 아님");
        }
        
        // 1. 상태 변경
        log.setStatus(CorrectionStatus.APPROVED);
        log.setReviewedBy(reviewedBy);
        log.setReviewedAt(LocalDateTime.now());
        correctionLogRepository.save(log);
        
        // 2. 패턴 학습
        learnPattern(log);
        
        // 3. Few-Shot 예시 생성 (필요 시)
        if (shouldGenerateFewShotExample(log)) {
            generateFewShotExample(log);
        }
        
        log.info("수정사항 승인 및 학습 완료: {}", correctionId);
    }
    
    /**
     * 패턴 학습 (버전 관리)
     */
    private void learnPattern(CorrectionLog log) {
        String patternKey = log.getInsuCd() + "_" + log.getFieldName();
        
        LearnedPattern existing = learnedPatternRepository
            .findByPatternKey(patternKey)
            .orElse(null);
        
        if (existing != null) {
            // 기존 패턴 업데이트 (버전 증가)
            existing.setPatternValue(log.getCorrectedValue());
            existing.setVersion(existing.getVersion() + 1);
            existing.setUpdatedAt(LocalDateTime.now());
            learnedPatternRepository.save(existing);
            log.info("패턴 업데이트: {} (v{})", patternKey, existing.getVersion());
        } else {
            // 새 패턴 생성
            LearnedPattern pattern = new LearnedPattern();
            pattern.setPatternKey(patternKey);
            pattern.setPatternValue(log.getCorrectedValue());
            pattern.setVersion(1);
            pattern.setActive(true);
            learnedPatternRepository.save(pattern);
            log.info("새 패턴 생성: {} (v1)", patternKey);
        }
    }
    
    /**
     * 패턴 롤백
     */
    public void rollbackPattern(String patternKey, int targetVersion) {
        LearnedPattern pattern = learnedPatternRepository
            .findByPatternKey(patternKey)
            .orElseThrow(() -> new IllegalArgumentException("패턴 없음"));
        
        // 버전 이력 조회 (별도 테이블 필요)
        // 여기서는 간단히 비활성화
        if (targetVersion == 0) {
            pattern.setActive(false);
            learnedPatternRepository.save(pattern);
            log.info("패턴 비활성화: {}", patternKey);
        }
    }
    
    /**
     * Canary 배포: 일부 PDF만 신버전 패턴 사용
     */
    public Map<String, String> applyPatternsWithCanary(String insuCd, 
                                                       Map<String, String> rawResult,
                                                       double canaryRatio) {
        
        // Canary 그룹 판정 (insuCd 해시 기반)
        boolean isCanary = (insuCd.hashCode() % 100) < (canaryRatio * 100);
        
        if (isCanary) {
            log.debug("Canary 그룹: 최신 패턴 사용 ({})", insuCd);
            return applyLatestPatterns(insuCd, rawResult);
        } else {
            log.debug("안정 그룹: 이전 패턴 사용 ({})", insuCd);
            return applyStablePatterns(insuCd, rawResult);
        }
    }
    
    private Map<String, String> applyLatestPatterns(String insuCd, Map<String, String> rawResult) {
        // 최신 버전 패턴 적용
        return rawResult;  // 구현 생략
    }
    
    private Map<String, String> applyStablePatterns(String insuCd, Map<String, String> rawResult) {
        // 검증된 안정 버전 패턴만 적용
        return rawResult;  // 구현 생략
    }
}

// Enum
public enum CorrectionStatus {
    PENDING,    // 승인 대기
    APPROVED,   // 승인됨
    REJECTED    // 거부됨
}
```

**장점:**
- ✅ 영속성 보장 (서버 재시작 후에도 유지)
- ✅ 승인 워크플로 (잘못된 학습 방지)
- ✅ 버전 관리 (롤백 가능)
- ✅ Canary 배포 (안전한 실험)
- ✅ 감사 추적 (누가 언제 수정)

**구현 가능성: ✅ 가능**
- 표준 JPA/MyBatis 활용
- 기존 DB 스키마 추가만 필요

---

## 🟡 문제점 5: 정규식 파싱 취약성

### 현재 구현 분석

```java
// BusinessMethodParsingStrategy.java
private String extractInsuranceTerm(String text) {
    Pattern pattern = Pattern.compile("보험기간[:\\s]*(종신|\\d+세만기|\\d+년만기)");
    // ❌ 문제: 단순 텍스트 매칭, 표 구조 무시
    Matcher matcher = pattern.matcher(text);
    // ...
}

// MultiLayerValidationService.java
private boolean isPdfTextConsistent(Map<String, String> terms, String pdfText) {
    // ... 
    // ❌ 문제: "50% 이상 일치" 기준이 모호
    return totalFields == 0 || ((double) matchCount / totalFields) >= 0.5;
}
```

### 문제점 상세 분석

#### 5.1 확인된 문제
✅ **분석 결과: 제시된 문제 적합**

- **레이아웃 무시**: PDFBox는 텍스트만 추출, 표 구조 손실
- **일치도 모호**: "50% 이상"의 구체적 정의 없음
- **도메인 규칙 하드코딩**: "보험기간 >= 납입기간" 등이 코드에 박혀있음
- **회귀 테스트 부재**: 파싱 로직 변경 시 검증 방법 없음

#### 5.2 영향도 평가
- **심각도**: 🟡 MEDIUM
- **발생 가능성**: 중간 (표 레이아웃이 다른 PDF에서 실패)
- **영향 범위**: 특정 PDF 형식에서 정확도 저하

### 개선 방안

#### 방안 5-A: 표 구조 인지 + 선언형 룰셋 (장기 - 우선순위 3)

```java
// TableAwareParsingStrategy.java (신규)
@Service
@Slf4j
public class TableAwareParsingStrategy implements ParsingStrategy {
    
    /**
     * 표 구조 기반 파싱
     */
    @Override
    public Map<String, String> parse(File pdfFile, String insuCd) {
        try {
            // 1. 표 구조 추출 (좌표 기반)
            List<TableRegion> tables = extractTables(pdfFile);
            
            // 2. 앵커 구역 찾기
            TableRegion termsTable = findTermsTable(tables);
            if (termsTable == null) {
                log.warn("보험 조건 표를 찾을 수 없음");
                return getEmptyResult();
            }
            
            // 3. 셀 기반 추출
            Map<String, String> terms = extractTermsFromTable(termsTable, insuCd);
            
            return terms;
            
        } catch (Exception e) {
            log.error("표 기반 파싱 실패: {}", e.getMessage());
            return getEmptyResult();
        }
    }
    
    /**
     * PDF에서 표 영역 추출 (Apache PDFBox + Tabula)
     */
    private List<TableRegion> extractTables(File pdfFile) throws IOException {
        List<TableRegion> tables = new ArrayList<>();
        
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTableExtractor extractor = new PDFTableExtractor();
            
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                PDPage page = document.getPage(i);
                List<Table> pageTables = extractor.extractTables(page);
                
                for (Table table : pageTables) {
                    TableRegion region = new TableRegion(i, table);
                    tables.add(region);
                }
            }
        }
        
        log.info("추출된 표 개수: {}", tables.size());
        return tables;
    }
    
    /**
     * "보험기간/납입기간/가입나이" 앵커로 조건 표 찾기
     */
    private TableRegion findTermsTable(List<TableRegion> tables) {
        for (TableRegion table : tables) {
            String headerText = table.getHeaderRow();
            
            // 앵커 키워드 확인
            if (headerText.contains("보험기간") && 
                headerText.contains("납입기간") &&
                headerText.contains("가입나이")) {
                
                log.info("조건 표 발견: 페이지 {}", table.getPageNumber());
                return table;
            }
        }
        return null;
    }
    
    /**
     * 표에서 셀 기반 데이터 추출
     */
    private Map<String, String> extractTermsFromTable(TableRegion table, String insuCd) {
        Map<String, String> terms = new HashMap<>();
        
        // 보험코드로 행 찾기
        int rowIndex = table.findRowByCode(insuCd);
        if (rowIndex == -1) {
            log.warn("표에서 보험코드 {} 행을 찾을 수 없음", insuCd);
            return getEmptyResult();
        }
        
        // 컬럼별 데이터 추출
        terms.put("insuTerm", table.getCell(rowIndex, "보험기간"));
        terms.put("payTerm", table.getCell(rowIndex, "납입기간"));
        terms.put("ageRange", table.getCell(rowIndex, "가입나이"));
        terms.put("renew", table.getCell(rowIndex, "갱신여부"));
        
        return terms;
    }
    
    @Data
    private static class TableRegion {
        private final int pageNumber;
        private final Table table;
        private final Map<String, Integer> columnIndex;
        
        public TableRegion(int pageNumber, Table table) {
            this.pageNumber = pageNumber;
            this.table = table;
            this.columnIndex = buildColumnIndex();
        }
        
        private Map<String, Integer> buildColumnIndex() {
            Map<String, Integer> index = new HashMap<>();
            List<String> headers = table.getRow(0);
            
            for (int i = 0; i < headers.size(); i++) {
                index.put(headers.get(i), i);
            }
            
            return index;
        }
        
        public String getHeaderRow() {
            return String.join(" ", table.getRow(0));
        }
        
        public int findRowByCode(String code) {
            for (int i = 1; i < table.getRowCount(); i++) {
                if (table.getRow(i).contains(code)) {
                    return i;
                }
            }
            return -1;
        }
        
        public String getCell(int row, String columnName) {
            Integer colIndex = columnIndex.get(columnName);
            if (colIndex == null) {
                return "—";
            }
            return table.getCell(row, colIndex);
        }
    }
}

// DomainRuleEngine.java (신규 - 선언형 룰셋)
@Service
@Slf4j
public class DomainRuleEngine {
    
    private List<ValidationRule> rules = new ArrayList<>();
    
    @PostConstruct
    public void initializeRules() {
        // 룰 1: 보험기간 >= 납입기간
        rules.add(new ValidationRule(
            "TERM_RELATIONSHIP",
            "보험기간은 납입기간보다 크거나 같아야 함",
            (terms) -> {
                int insuYears = parseInsuTerm(terms.get("insuTerm"));
                int payYears = parsePayTerm(terms.get("payTerm"));
                return insuYears == 999 || insuYears >= payYears;
            }
        ));
        
        // 룰 2: 가입나이 범위 0-120
        rules.add(new ValidationRule(
            "AGE_RANGE",
            "가입나이는 0~120세 범위 내여야 함",
            (terms) -> {
                String ageRange = terms.get("ageRange");
                return isAgeRangeValid(ageRange);
            }
        ));
        
        // 룰 3: 갱신형은 단기 보험기간만
        rules.add(new ValidationRule(
            "RENEWAL_TYPE",
            "갱신형은 종신 불가",
            (terms) -> {
                String renew = terms.get("renew");
                String insuTerm = terms.get("insuTerm");
                if ("갱신형".equals(renew)) {
                    return !insuTerm.contains("종신");
                }
                return true;
            }
        ));
        
        log.info("도메인 룰 {} 개 로드 완료", rules.size());
    }
    
    /**
     * 모든 룰 검증
     */
    public RuleValidationResult validate(Map<String, String> terms) {
        List<String> violations = new ArrayList<>();
        
        for (ValidationRule rule : rules) {
            try {
                if (!rule.test(terms)) {
                    violations.add(rule.getDescription());
                    log.warn("룰 위반: {} - {}", rule.getRuleId(), rule.getDescription());
                }
            } catch (Exception e) {
                log.error("룰 실행 오류: {}", rule.getRuleId(), e);
            }
        }
        
        boolean passed = violations.isEmpty();
        return new RuleValidationResult(passed, violations);
    }
    
    @Data
    private static class ValidationRule {
        private final String ruleId;
        private final String description;
        private final Predicate<Map<String, String>> predicate;
        
        public boolean test(Map<String, String> terms) {
            return predicate.test(terms);
        }
    }
    
    @Data
    public static class RuleValidationResult {
        private final boolean passed;
        private final List<String> violations;
    }
}
```

**장점:**
- ✅ 표 구조 인식으로 정확도 향상
- ✅ 선언형 룰셋으로 유지보수 편리
- ✅ 룰 추가/수정이 쉬움

**단점:**
- ⚠️ Tabula 등 외부 라이브러리 필요
- ⚠️ 복잡한 표 레이아웃에서 한계

**구현 가능성: ⚠️ 조건부 가능**
- Tabula-java 또는 PDF Table Extractor 라이브러리 필요
- 표 구조가 일정하지 않으면 한계

---

## 📊 우선순위별 구현 로드맵

### 🔴 즉시 구현 (1-2주)

| 우선순위 | 문제 | 해결 방안 | 예상 소요 | 난이도 |
|---------|------|----------|----------|--------|
| **P0** | LLM 병렬 처리 | 쿼럼 기반 조기 종료 | 3일 | 중 |
| **P1** | 인메모리 캐시 | Caffeine 도입 | 2일 | 하 |

**이유:**
- LLM 병렬 처리는 사용자 경험에 직접 영향 (응답 시간 50% 단축)
- Caffeine은 기존 코드 수정 최소로 즉시 효과

### 🟡 단기 구현 (1개월)

| 우선순위 | 문제 | 해결 방안 | 예상 소요 | 난이도 |
|---------|------|----------|----------|--------|
| **P2** | 점진 학습 영속성 | DB 영속화 + 워크플로 | 5일 | 중 |
| **P3** | 인메모리 캐시 | Redis 분산 캐시 | 3일 | 중 |
| **P4** | LLM 안정성 | 서킷브레이커 | 2일 | 하 |

**이유:**
- 운영 환경에서는 영속성과 분산 캐시 필수
- 서킷브레이커는 안정성 향상

### 🔵 중기 구현 (3개월)

| 우선순위 | 문제 | 해결 방안 | 예상 소요 | 난이도 |
|---------|------|----------|----------|--------|
| **P5** | 신뢰도 점수 | 학습 기반 모델 | 10일 | 상 |
| **P6** | 정규식 파싱 | 표 구조 인지 | 7일 | 상 |

**이유:**
- 정확도 개선은 점진적으로 진행 가능
- 검증 데이터셋 구축 시간 필요

---

## ✅ 결론 및 권장사항

### 종합 평가

| 문제 | 적합성 | 심각도 | 구현 가능성 | 권장 |
|------|--------|--------|------------|------|
| 1. 인메모리 캐시 | ✅ 매우 적합 | 🔴 HIGH | ✅ 즉시 가능 | **즉시 구현** |
| 2. LLM 병렬 처리 | ✅ 매우 적합 | 🔴 HIGH | ✅ 즉시 가능 | **즉시 구현** |
| 3. 신뢰도 휴리스틱 | ✅ 적합 | 🟡 MEDIUM | ⚠️ 조건부 | 중기 구현 |
| 4. 학습 영속성 | ✅ 매우 적합 | 🟡 MEDIUM | ✅ 가능 | 단기 구현 |
| 5. 정규식 파싱 | ✅ 적합 | 🟡 MEDIUM | ⚠️ 조건부 | 중기 구현 |

### 최종 권장 로드맵

#### Phase 1: 즉시 개선 (2주)
1. ✅ **Caffeine Cache 도입** (2일)
2. ✅ **쿼럼 기반 LLM 파싱** (3일)
3. ✅ **캐시 메트릭 대시보드** (2일)

**예상 효과:**
- 응답 시간: 30초 → 15초 (50% 개선)
- 메모리 안정성: 무제한 → 1000개 제한
- 캐시 히트율 가시화

#### Phase 2: 안정화 (1개월)
4. ✅ **Redis 분산 캐시** (3일)
5. ✅ **서킷브레이커 + 헤지드 요청** (2일)
6. ✅ **DB 영속화 + 승인 워크플로** (5일)

**예상 효과:**
- 스케일아웃 지원
- LLM 장애 대응 자동화
- 학습 데이터 보존 및 거버넌스

#### Phase 3: 정확도 향상 (3개월)
7. ⚠️ **학습 기반 신뢰도 모델** (10일)
8. ⚠️ **표 구조 인지 파싱** (7일)
9. ⚠️ **회귀 테스트 자동화** (5일)

**예상 효과:**
- 정확도: 95% → 97%+
- 다양한 PDF 형식 대응
- 파싱 로직 변경 안정성

---

**작성일**: 2025-10-11  
**버전**: 1.0  
**상태**: ✅ 분석 완료 - 구현 대기


