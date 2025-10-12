# 협업 가이드

## 🎯 협업 환경 구축 방법

### 1. Git 저장소 공유 방법

#### A. GitHub 사용 (권장)
```bash
# 1. GitHub에서 새 저장소 생성
# 2. 로컬 저장소와 연결
git remote add origin https://github.com/username/insu-app.git
git branch -M main
git push -u origin main
```

#### B. GitLab 사용
```bash
git remote add origin https://gitlab.com/username/insu-app.git
git branch -M main
git push -u origin main
```

#### C. Bitbucket 사용
```bash
git remote add origin https://bitbucket.org/username/insu-app.git
git branch -M main
git push -u origin main
```

#### D. 로컬 네트워크 공유
```bash
# 공유 폴더에 bare 저장소 생성
git init --bare //server/shared/insu-app.git

# 로컬 저장소와 연결
git remote add origin //server/shared/insu-app.git
git push -u origin main
```

### 2. 개발 환경 동기화

#### A. 환경 설정 파일
```bash
# 공유할 설정 파일
cp backend/src/main/resources/application-local.yml.example \
   backend/src/main/resources/application-local.yml

# .env 파일 생성 (민감한 정보 제외)
echo "DATABASE_URL=jdbc:oracle:thin:@localhost:1521:xe" > .env.example
echo "DATABASE_USERNAME=your_username" >> .env.example
echo "DATABASE_PASSWORD=your_password" >> .env.example
```

#### B. 의존성 설치 스크립트
```bash
# setup-dev.sh (Linux/Mac)
#!/bin/bash
echo "개발 환경 설정 시작..."

# Java 확인
java -version

# Node.js 확인
node --version
npm --version

# 백엔드 의존성 설치
cd backend
./mvnw clean compile

# 프론트엔드 의존성 설치
cd ../insu_ui
npm install

echo "개발 환경 설정 완료!"
```

```powershell
# setup-dev.ps1 (Windows)
Write-Host "개발 환경 설정 시작..."

# Java 확인
java -version

# Node.js 확인
node --version
npm --version

# 백엔드 의존성 설치
cd backend
.\mvnw.cmd clean compile

# 프론트엔드 의존성 설치
cd ..\insu_ui
npm install

Write-Host "개발 환경 설정 완료!"
```

### 3. 협업 워크플로우

#### A. 브랜치 전략
```
main (프로덕션)
├── develop (개발 통합)
    ├── feature/pdf-parser-improvement
    ├── feature/llm-integration
    ├── feature/ui-enhancement
    └── bugfix/parsing-error-fix
```

#### B. 개발 프로세스
```bash
# 1. 최신 코드 동기화
git checkout develop
git pull origin develop

# 2. 기능 브랜치 생성
git checkout -b feature/new-feature

# 3. 개발 및 커밋
git add .
git commit -m "feat: 새로운 기능 추가"

# 4. 푸시 및 Pull Request 생성
git push origin feature/new-feature
# GitHub/GitLab에서 Pull Request 생성
```

#### C. 코드 리뷰 체크리스트
- [ ] 코드가 프로젝트 스타일 가이드를 따르는가?
- [ ] 테스트가 포함되어 있는가?
- [ ] 문서가 업데이트되었는가?
- [ ] 성능에 영향을 주는 변경사항인가?
- [ ] 보안 취약점이 없는가?

### 4. 의사소통 방법

#### A. 이슈 관리
```markdown
## 버그 리포트 템플릿
**버그 설명**: 
**재현 단계**:
1. 
2. 
3. 
**예상 결과**:
**실제 결과**:
**환경 정보**:
- OS: 
- Java 버전: 
- Node.js 버전: 

## 기능 요청 템플릿
**기능 설명**:
**사용 사례**:
**대안 검토**:
```

#### B. Pull Request 템플릿
```markdown
## 변경 사항 요약
- 

## 변경 유형
- [ ] 버그 수정
- [ ] 새로운 기능
- [ ] 문서 업데이트
- [ ] 리팩토링

## 테스트
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 수동 테스트 완료

## 체크리스트
- [ ] 코드 리뷰 완료
- [ ] 문서 업데이트
- [ ] 테스트 추가
```

### 5. 도구 및 설정

#### A. IDE 설정 (VSCode)
```json
// .vscode/settings.json
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "java.compile.nullAnalysis.mode": "automatic",
  "editor.formatOnSave": true,
  "editor.codeActionsOnSave": {
    "source.organizeImports": true
  },
  "typescript.preferences.importModuleSpecifier": "relative"
}
```

#### B. Git Hooks
```bash
# .git/hooks/pre-commit
#!/bin/sh
echo "커밋 전 검사 실행..."

# Java 코드 포맷팅 검사
cd backend
./mvnw.cmd checkstyle:check

# TypeScript 린팅 검사
cd ../insu_ui
npm run lint

echo "검사 완료!"
```

### 6. 데이터베이스 공유

#### A. 스키마 버전 관리
```sql
-- migrations/V001__initial_schema.sql
-- migrations/V002__add_uw_code_mapping.sql
-- migrations/V003__extend_uw_code_mapping.sql
```

#### B. 샘플 데이터 공유
```sql
-- sample-data/uw_code_mapping_sample.sql
INSERT INTO UW_CODE_MAPPING VALUES (...);
```

### 7. 보안 고려사항

#### A. 민감한 정보 제외
```bash
# .gitignore에 추가
.env
application-local.yml
application-prod.yml
*.key
*.p12
```

#### B. 환경별 설정 분리
```yaml
# application.yml (공유)
spring:
  profiles:
    active: local

# application-local.yml (개인)
spring:
  datasource:
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

### 8. 문제 해결

#### A. 일반적인 문제
```bash
# 의존성 충돌 해결
cd backend
./mvnw.cmd dependency:tree
./mvnw.cmd clean install

# Node.js 캐시 클리어
cd insu_ui
npm cache clean --force
rm -rf node_modules
npm install
```

#### B. 데이터베이스 연결 문제
```bash
# Oracle 연결 테스트
sqlplus user/password@//localhost:1521/xe

# Spring Boot 로그 확인
tail -f logs/application.log
```

### 9. 성능 모니터링

#### A. 애플리케이션 모니터링
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
  endpoint:
    health:
      show-details: always
```

#### B. 로깅 설정
```yaml
# logback-spring.xml
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>
  
  <logger name="com.example.insu" level="DEBUG"/>
  <root level="INFO">
    <appender-ref ref="STDOUT"/>
  </root>
</configuration>
```

### 10. 배포 및 운영

#### A. Docker 설정
```dockerfile
# Dockerfile
FROM openjdk:17-jdk-slim
COPY backend/target/insu-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

#### B. CI/CD 파이프라인
```yaml
# .github/workflows/ci.yml
name: CI/CD Pipeline
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK 17
        uses: actions/setup-java@v2
        with:
          java-version: '17'
      - name: Run tests
        run: cd backend && ./mvnw test
```

이 가이드를 따라하면 효과적인 협업 환경을 구축할 수 있습니다.
