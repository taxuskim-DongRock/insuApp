# Python 환경 설정 가이드

## 📋 개요

이 가이드는 보험 인수 문서 파싱 시스템의 Python 환경을 설정하는 방법을 설명합니다.

## 🚀 빠른 시작

### 자동 설정 (권장)
```powershell
# 전체 환경 설정 (모든 패키지)
.\setup-python-env.ps1

# 최소 환경 설정 (핵심 패키지만)
.\setup-python-env.ps1 -Environment minimal

# 개발 환경 설정 (개발 도구 포함)
.\setup-python-env.ps1 -Environment dev

# AI 환경 설정 (LLM 패키지 포함)
.\setup-python-env.ps1 -Environment ai
```

## 📦 Requirements 파일 설명

### `requirements.txt` (전체)
모든 기능을 위한 완전한 패키지 목록
- PDF 처리: PyPDF2, pdf2image, pdfplumber, pymupdf
- OCR: pytesseract, Pillow, opencv-python
- 데이터 처리: pandas, numpy, openpyxl
- 데이터베이스: cx_Oracle, sqlalchemy
- AI/LLM: openai, transformers, torch
- 개발 도구: jupyter, pytest, black

### `requirements-minimal.txt` (최소)
핵심 기능만을 위한 패키지 목록
- PDF 처리: PyPDF2, pdf2image
- OCR: pytesseract, Pillow
- 데이터 처리: pandas, numpy
- HTTP 요청: requests
- 데이터베이스: cx_Oracle
- 유틸리티: pydantic, loguru, tqdm

### `requirements-dev.txt` (개발)
개발 도구가 포함된 환경
- 기본 requirements.txt + 개발 도구
- 테스트: pytest, pytest-cov, pytest-mock
- 코드 품질: black, flake8, mypy, isort
- 문서화: sphinx
- 디버깅: ipdb, pdb++

### `requirements-ai.txt` (AI)
AI/LLM 관련 패키지
- OpenAI API: openai
- Anthropic API: anthropic
- Transformers: transformers, torch
- Ollama: ollama
- 벡터 DB: chromadb, faiss-cpu
- NLP: spacy, nltk

## 🛠️ 수동 설정

### 1. Python 설치
- Python 3.8+ 필요 (권장: 3.11)
- [Python 공식 사이트](https://www.python.org/downloads/)에서 다운로드
- 설치 시 "Add Python to PATH" 체크

### 2. 가상환경 생성
```bash
# 가상환경 생성
python -m venv insu-env

# 가상환경 활성화 (Windows)
insu-env\Scripts\activate

# 가상환경 활성화 (Linux/Mac)
source insu-env/bin/activate
```

### 3. 패키지 설치
```bash
# 전체 패키지 설치
pip install -r requirements.txt

# 최소 패키지 설치
pip install -r requirements-minimal.txt

# 개발 도구 포함 설치
pip install -r requirements-dev.txt

# AI 패키지 설치
pip install -r requirements-ai.txt
```

## 🔧 환경별 설정

### 개발 환경
```bash
# 개발 환경 설정
.\setup-python-env.ps1 -Environment dev

# 가상환경 활성화
& "insu-env\Scripts\Activate.ps1"

# Jupyter Notebook 실행
jupyter notebook

# 테스트 실행
pytest

# 코드 포맷팅
black .
flake8 .
```

### 프로덕션 환경
```bash
# 최소 환경 설정
.\setup-python-env.ps1 -Environment minimal

# 가상환경 활성화
& "insu-env\Scripts\Activate.ps1"

# 배치 파싱 실행
python batch_parse_all.py
```

### AI 연구 환경
```bash
# AI 환경 설정
.\setup-python-env.ps1 -Environment ai

# 가상환경 활성화
& "insu-env\Scripts\Activate.ps1"

# Ollama 설치 및 실행
ollama pull llama3.1:8b
ollama serve

# Python에서 LLM 테스트
python -c "import ollama; print(ollama.list())"
```

## 📊 패키지별 용도

### PDF 처리
| 패키지 | 용도 | 필요도 |
|--------|------|--------|
| PyPDF2 | 기본 PDF 텍스트 추출 | 필수 |
| pdf2image | PDF를 이미지로 변환 | 필수 |
| pdfplumber | 고급 PDF 파싱 | 권장 |
| pymupdf | 빠른 PDF 처리 | 선택 |

### OCR
| 패키지 | 용도 | 필요도 |
|--------|------|--------|
| pytesseract | OCR 엔진 | 필수 |
| Pillow | 이미지 처리 | 필수 |
| opencv-python | 이미지 전처리 | 권장 |

### 데이터 처리
| 패키지 | 용도 | 필요도 |
|--------|------|--------|
| pandas | 데이터 분석 | 필수 |
| numpy | 수치 계산 | 필수 |
| openpyxl | Excel 파일 처리 | 선택 |

### 데이터베이스
| 패키지 | 용도 | 필요도 |
|--------|------|--------|
| cx_Oracle | Oracle DB 연결 | 필수 |
| sqlalchemy | ORM | 권장 |

### AI/LLM
| 패키지 | 용도 | 필요도 |
|--------|------|--------|
| openai | OpenAI API | 선택 |
| transformers | Hugging Face 모델 | 선택 |
| torch | PyTorch | 선택 |
| ollama | 로컬 LLM | 선택 |

## 🐛 문제 해결

### 일반적인 문제

#### 1. pip 설치 실패
```bash
# pip 업그레이드
python -m pip install --upgrade pip

# 캐시 클리어
pip cache purge

# 개별 패키지 설치
pip install --no-cache-dir package_name
```

#### 2. Oracle 연결 오류
```bash
# Oracle Instant Client 설치 필요
# Windows: Oracle Instant Client 다운로드 후 PATH 설정
# Linux: libaio1 설치 필요
sudo apt-get install libaio1
```

#### 3. Tesseract OCR 오류
```bash
# Windows: Tesseract 설치 필요
# https://github.com/UB-Mannheim/tesseract/wiki

# Linux: Tesseract 설치
sudo apt-get install tesseract-ocr

# macOS: Homebrew로 설치
brew install tesseract
```

#### 4. 메모리 부족
```bash
# 대용량 파일 처리 시 메모리 부족 발생 가능
# 배치 크기 조정 또는 파일 분할 처리
```

### 환경별 문제

#### Windows
- PowerShell 실행 정책: `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`
- Visual C++ Redistributable 설치 필요 (일부 패키지용)
- Oracle Instant Client PATH 설정

#### Linux
- 시스템 패키지 설치: `sudo apt-get install python3-dev python3-pip`
- Oracle Instant Client 설치
- Tesseract OCR 설치

#### macOS
- Xcode Command Line Tools: `xcode-select --install`
- Homebrew로 시스템 패키지 관리

## 📈 성능 최적화

### 패키지 설치 최적화
```bash
# 바이너리 휠 사용 (빠른 설치)
pip install --only-binary=all package_name

# 병렬 설치
pip install --upgrade pip
pip install -U pip setuptools wheel
```

### 실행 최적화
```bash
# CPU 코어 수에 맞는 병렬 처리
import multiprocessing
n_cores = multiprocessing.cpu_count()

# 메모리 효율적인 처리
import gc
gc.collect()  # 가비지 컬렉션
```

## 🔄 업데이트

### 패키지 업데이트
```bash
# 가상환경 활성화
& "insu-env\Scripts\Activate.ps1"

# 패키지 업데이트
pip list --outdated
pip install --upgrade package_name

# requirements.txt 업데이트
pip freeze > requirements.txt
```

### 환경 재생성
```bash
# 기존 환경 삭제
Remove-Item -Recurse -Force insu-env

# 새 환경 생성
.\setup-python-env.ps1
```

## 📞 지원

문제가 발생하거나 질문이 있으시면:
1. 이슈 생성: GitHub Issues
2. 문서 확인: README.md
3. 로그 확인: python-env-info.txt

---

**주의사항**:
- 가상환경을 사용하여 시스템 Python과 격리
- 프로덕션 환경에서는 최소 패키지만 설치
- 정기적으로 패키지 보안 업데이트 확인
