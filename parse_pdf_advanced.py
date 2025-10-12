#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
고급 PDF 파싱 시스템 (Phase 2)
- PDF 유형 자동 감지
- 유형별 맞춤 파서
- 95% 정확도 목표
"""

import sys
import json
import re
from pathlib import Path
import PyPDF2
from typing import Dict, List, Optional

class PDFTypeDetector:
    """PDF 유형 자동 감지"""
    
    @staticmethod
    def detect(text: str) -> str:
        """PDF 유형 감지"""
        if not text or len(text) < 100:
            return "EMPTY"
        
        # 간편건강보험 (325, 335, 355)
        if "간편건강보험" in text or "간편심사형" in text:
            return "SIMPLE_HEALTH"
        
        # 표준 표 형식
        if "구 분 보험기간" in text or "보험기간보험료" in text:
            return "STANDARD_TABLE"
        
        # 간단한 구조
        if len(text) < 5000:
            return "SIMPLE"
        
        # 복잡한 구조
        return "COMPLEX"

class SimpleHealthInsuranceParser:
    """간편건강보험 전용 파서"""
    
    def __init__(self):
        self.debug = True
    
    def log(self, message: str):
        if self.debug:
            print(f"[SimpleHealthParser] {message}", file=sys.stderr)
    
    def parse(self, text: str, insu_cd: str) -> Dict:
        """간편건강보험 파싱"""
        self.log(f"간편건강보험 파싱 시작: {insu_cd}")
        
        result = {
            'insuTerm': [],
            'payTerm': [],
            'ageRange': [],
            'renew': ''
        }
        
        # 보험기간: "10년", "20년" 등
        term_matches = re.findall(r'(\d+)년(?!\s*납)', text)
        for match in term_matches:
            term = f"{match}년"
            if term not in result['insuTerm'] and int(match) <= 50:  # 50년 이하만
                result['insuTerm'].append(term)
                self.log(f"보험기간: {term}")
        
        # 납입기간: "전기납"
        if '전기납' in text:
            result['payTerm'].append('전기납')
            self.log("납입기간: 전기납")
        
        # 가입나이: "15세 ~ 80세" 등
        age_matches = re.findall(r'(\d+)세\s*~\s*(\d+)세', text)
        for match in age_matches:
            age_range = f"{match[0]}세~{match[1]}세"
            if age_range not in result['ageRange']:
                result['ageRange'].append(age_range)
                self.log(f"가입나이: {age_range}")
        
        # 갱신: 항상 갱신형
        if '갱신' in text:
            result['renew'] = '갱신형'
            self.log("갱신: 갱신형")
        
        return result

class AdvancedPDFParser:
    """고급 PDF 파싱 시스템"""
    
    def __init__(self):
        self.debug = True
        self.detector = PDFTypeDetector()
        self.simple_health_parser = SimpleHealthInsuranceParser()
    
    def log(self, message: str):
        if self.debug:
            print(f"[AdvancedParser] {message}", file=sys.stderr)
    
    def extract_text(self, pdf_path: str) -> str:
        """PDF에서 텍스트 추출"""
        try:
            with open(pdf_path, 'rb') as file:
                pdf_reader = PyPDF2.PdfReader(file)
                text = ""
                for page in pdf_reader.pages:
                    text += page.extract_text() + "\n"
                return text
        except Exception as e:
            self.log(f"텍스트 추출 실패: {e}")
            return ""
    
    def parse_standard_table(self, text: str) -> Dict:
        """표준 표 형식 파싱"""
        result = {
            'insuTerm': [],
            'payTerm': [],
            'ageRange': [],
            'renew': ''
        }
        
        # 보험기간
        term_patterns = [
            (r'종신', 'lifetime'),
            (r'평생', 'lifetime'),
            (r'(\d+)세\s*까지', 'age_until'),
            (r'(\d+)세만기', 'age'),
            (r'(\d+)년만기', 'year'),
            (r'(\d+)년(?=\s+일시납|\s+전기납)', 'year_pay')
        ]
        
        for pattern, ptype in term_patterns:
            if ptype == 'lifetime':
                if re.search(pattern, text):
                    result['insuTerm'].append(pattern)
            else:
                matches = re.findall(pattern, text)
                for match in matches:
                    if ptype == 'age':
                        term = f"{match}세만기"
                    elif ptype == 'age_until':
                        term = f"{match}세까지"
                    elif ptype == 'year_pay' or ptype == 'year':
                        term = f"{match}년"
                    else:
                        term = match
                    
                    if term not in result['insuTerm']:
                        result['insuTerm'].append(term)
        
        # 납입기간
        if '전기납' in text:
            result['payTerm'].append('전기납')
        if '일시납' in text:
            result['payTerm'].append('일시납')
        
        pay_matches = re.findall(r'(\d+)년납', text)
        for match in pay_matches:
            term = f"{match}년납"
            if term not in result['payTerm']:
                result['payTerm'].append(term)
        
        # 가입나이
        age_patterns = [
            r'만\s*(\d+)세\s*~\s*(\d+)세',
            r'(\d+)세\s*~\s*(\d+)세',
            r'(\d+)세~(\d+)세'
        ]
        
        for pattern in age_patterns:
            matches = re.findall(pattern, text)
            for match in matches:
                age_range = f"{match[0]}세~{match[1]}세"
                if age_range not in result['ageRange']:
                    result['ageRange'].append(age_range)
        
        # 갱신
        if '갱신' in text:
            result['renew'] = '갱신형'
        
        return result
    
    def parse_product_codes(self, text: str) -> Dict:
        """상품 코드 추출"""
        codes = {}
        code_pattern = re.compile(r'\b(\d{5})\b')
        matches = code_pattern.findall(text)
        
        for code in matches:
            context_pattern = re.compile(rf'([^\n]*?)\s*{code}\s*([^\n]*)')
            context_match = context_pattern.search(text)
            if context_match:
                name = (context_match.group(1) + context_match.group(2)).strip()
                codes[code] = name[:100]
        
        return codes
    
    def parse(self, pdf_path: str, insu_cd: str) -> Dict:
        """PDF 파싱 메인 함수"""
        self.log(f"파싱 시작: {pdf_path}")
        
        # 텍스트 추출
        text = self.extract_text(pdf_path)
        if not text:
            return {"error": "텍스트 추출 실패", "success": False}
        
        # PDF 유형 감지
        pdf_type = self.detector.detect(text)
        self.log(f"PDF 유형: {pdf_type}")
        
        # 유형별 파싱
        if pdf_type == "SIMPLE_HEALTH":
            terms_data = self.simple_health_parser.parse(text, insu_cd)
        elif pdf_type == "STANDARD_TABLE":
            terms_data = self.parse_standard_table(text)
        elif pdf_type == "SIMPLE":
            terms_data = self.parse_standard_table(text)
        else:
            # COMPLEX 또는 EMPTY
            terms_data = self.parse_standard_table(text)
        
        # 결과 구성
        terms = {
            'insuTerm': ', '.join(terms_data['insuTerm']) if terms_data['insuTerm'] else '',
            'payTerm': ', '.join(terms_data['payTerm']) if terms_data['payTerm'] else '',
            'ageRange': ', '.join(terms_data['ageRange']) if terms_data['ageRange'] else '',
            'renew': terms_data['renew'],
            'specialNotes': ''
        }
        
        # 상품 코드 추출
        codes = self.parse_product_codes(text)
        
        self.log(f"추출 완료 - 보험기간: {bool(terms['insuTerm'])}, "
                f"납입기간: {bool(terms['payTerm'])}, "
                f"가입나이: {bool(terms['ageRange'])}, "
                f"갱신: {bool(terms['renew'])}, "
                f"코드: {len(codes)}개")
        
        return {
            "insuCd": insu_cd,
            "pdfType": pdf_type,
            "terms": terms,
            "codes": codes,
            "success": True,
            "method": "advanced"
        }

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print(json.dumps({"error": "Usage: python parse_pdf_advanced.py <pdf_path> <insu_cd>"}), file=sys.stderr)
        sys.exit(1)
    
    pdf_path = sys.argv[1]
    insu_cd = sys.argv[2]
    
    parser = AdvancedPDFParser()
    result = parser.parse(pdf_path, insu_cd)
    print(json.dumps(result, ensure_ascii=False, indent=2))



