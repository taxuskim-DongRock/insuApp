#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PDF 파싱을 위한 Python 스크립트
Java 백엔드에서 호출하여 PDF에서 보험기간, 납입기간, 가입나이 정보를 추출
"""

import sys
import json
import re
import os
from pathlib import Path
import PyPDF2
from typing import Dict, List, Optional, Tuple

class PDFParser:
    def __init__(self):
        self.debug = True
    
    def log(self, message: str):
        if self.debug:
            print(f"[PDFParser] {message}", file=sys.stderr)
    
    def extract_text_from_pdf(self, pdf_path: str) -> str:
        """PDF에서 텍스트 추출"""
        try:
            with open(pdf_path, 'rb') as file:
                pdf_reader = PyPDF2.PdfReader(file)
                text = ""
                for page in pdf_reader.pages:
                    text += page.extract_text()
                return text
        except Exception as e:
            self.log(f"PDF 텍스트 추출 실패: {e}")
            return ""
    
    def find_sections(self, text: str) -> Dict[str, str]:
        """PDF 텍스트를 섹션별로 분리"""
        sections = {
            'block3': '',  # 3.보험코드
            'block4': '',  # 4.사업방법
            'block5': ''   # 5.가입한도
        }
        
        lines = text.split('\n')
        current_section = None
        
        for line in lines:
            line = line.strip()
            if line.startswith('3.'):
                current_section = 'block3'
                continue
            elif line.startswith('4.'):
                current_section = 'block4'
                continue
            elif line.startswith('5.'):
                current_section = 'block5'
                continue
            
            if current_section:
                sections[current_section] += line + '\n'
        
        return sections
    
    def parse_insurance_terms(self, block4: str) -> Dict[str, str]:
        """4.사업방법 섹션에서 보험기간, 납입기간, 가입나이 추출"""
        terms = {
            'insuTerm': '',
            'payTerm': '',
            'ageRange': '',
            'renew': '',
            'specialNotes': ''
        }
        
        if not block4:
            return terms
        
        # 보험기간 패턴들 (실제 PDF 내용에 맞춤)
        insu_term_patterns = [
            r'보험기간[:\s]*([^\n]+)',
            r'보험기간[:\s]*([^\n]+?)(?=\n|$)',
            r'보험기간[:\s]*([가-힣0-9\s,~-]+)',
            r'보험기간[:\s]*([0-9]+년)',
            r'보험기간[:\s]*([0-9]+세)',
            r'보험기간[:\s]*([0-9]+년[^\n]*)',
        ]
        
        # 납입기간 패턴들
        pay_term_patterns = [
            r'납입기간[:\s]*([^\n]+)',
            r'납입기간[:\s]*([^\n]+?)(?=\n|$)',
            r'납입기간[:\s]*([가-힣0-9\s,~-]+)',
            r'납입기간[:\s]*([0-9]+년)',
            r'납입기간[:\s]*([0-9]+세)',
            r'납입기간[:\s]*([0-9]+년[^\n]*)',
        ]
        
        # 가입나이 패턴들 (실제 PDF 내용에 맞춤)
        age_patterns = [
            r'가입나이[:\s]*([^\n]+)',
            r'가입나이[:\s]*([^\n]+?)(?=\n|$)',
            r'가입나이[:\s]*([가-힣0-9\s,~-]+)',
            r'만\s*([0-9]+)세[~-]([0-9]+)세',
            r'([0-9]+)세[~-]([0-9]+)세',
            r'([0-9]+)세[~-]([0-9]+)세',
            r'([0-9]+)세[~-]([0-9]+)세',
            r'([0-9]+)세[~-]([0-9]+)세',
            r'([0-9]+)세[~-]([0-9]+)세',
        ]
        
        # 갱신 여부 패턴들
        renew_patterns = [
            r'갱신[:\s]*([^\n]+)',
            r'갱신[:\s]*([^\n]+?)(?=\n|$)',
            r'갱신[:\s]*([가-힣0-9\s,~-]+)',
        ]
        
        # 보험기간 추출
        for pattern in insu_term_patterns:
            match = re.search(pattern, block4, re.IGNORECASE)
            if match:
                terms['insuTerm'] = match.group(1).strip()
                break
        
        # 납입기간 추출
        for pattern in pay_term_patterns:
            match = re.search(pattern, block4, re.IGNORECASE)
            if match:
                terms['payTerm'] = match.group(1).strip()
                break
        
        # 가입나이 추출
        for pattern in age_patterns:
            match = re.search(pattern, block4, re.IGNORECASE)
            if match:
                if len(match.groups()) >= 2:
                    terms['ageRange'] = f"{match.group(1)}세~{match.group(2)}세"
                else:
                    terms['ageRange'] = match.group(1).strip()
                break
        
        # 갱신 여부 추출
        for pattern in renew_patterns:
            match = re.search(pattern, block4, re.IGNORECASE)
            if match:
                terms['renew'] = match.group(1).strip()
                break
        
        # 특이사항 추출
        special_patterns = [
            r'특이사항[:\s]*([^\n]+)',
            r'주의사항[:\s]*([^\n]+)',
            r'기타[:\s]*([^\n]+)',
        ]
        
        for pattern in special_patterns:
            match = re.search(pattern, block4, re.IGNORECASE)
            if match:
                terms['specialNotes'] = match.group(1).strip()
                break
        
        return terms
    
    def parse_product_codes(self, block3: str) -> Dict[str, str]:
        """3.보험코드 섹션에서 상품 코드와 명칭 추출"""
        codes = {}
        
        if not block3:
            return codes
        
        # 5자리 숫자 코드와 상품명 매칭
        patterns = [
            r'([가-힣\\s\\(\\)\\[\\]A-Za-z0-9\\-]+?)\\s+(\\d{5})',
            r'(\\d{5})\\s+([가-힣\\s\\(\\)\\[\\]A-Za-z0-9\\-]+)',
        ]
        
        for pattern in patterns:
            matches = re.findall(pattern, block3)
            for match in matches:
                if len(match) == 2:
                    name, code = match
                    codes[code.strip()] = name.strip()
        
        return codes
    
    def parse_limits(self, block5: str, product_name: str, age: int = 15) -> Dict[str, any]:
        """5.가입한도 섹션에서 한도 정보 추출"""
        limits = {
            'minWon': None,
            'maxWon': None,
            'matchedLine': ''
        }
        
        if not block5 or not product_name:
            return limits
        
        # 나이대별 밴드 결정
        age_bands = {
            '60세이하': age <= 60,
            '61~65세': 61 <= age <= 65,
            '66~70세': 66 <= age <= 70,
            '71~75세': 71 <= age <= 75,
            '76~80세': 76 <= age <= 80,
            '81세이상': age >= 81
        }
        
        # 상품명이 포함된 라인 찾기
        lines = block5.split('\n')
        for line in lines:
            if product_name in line or any(keyword in line for keyword in ['다사랑', '3N5', '주계약']):
                # 숫자 추출
                numbers = re.findall(r'([0-9,]+)\\s*(만|억|원)?', line)
                if numbers:
                    # 첫 번째 숫자를 최대값으로, 마지막 숫자를 최소값으로 간주
                    if len(numbers) >= 2:
                        max_val = self.parse_amount(numbers[0][0], numbers[0][1])
                        min_val = self.parse_amount(numbers[-1][0], numbers[-1][1])
                        limits['maxWon'] = max_val
                        limits['minWon'] = min_val
                        limits['matchedLine'] = line
                        break
        
        return limits
    
    def parse_amount(self, number_str: str, unit: str = '') -> Optional[int]:
        """금액 문자열을 원 단위로 변환"""
        try:
            # 쉼표 제거
            number = int(number_str.replace(',', ''))
            
            if unit == '만':
                return number * 10000
            elif unit == '억':
                return number * 100000000
            elif unit == '원' or unit == '':
                return number
            else:
                return number
        except:
            return None
    
    def parse_pdf(self, pdf_path: str, insu_cd: str = None) -> Dict[str, any]:
        """PDF 파싱 메인 함수"""
        try:
            # PDF 텍스트 추출
            text = self.extract_text_from_pdf(pdf_path)
            if not text:
                return {'error': 'PDF 텍스트 추출 실패'}
            
            # 섹션별 분리
            sections = self.find_sections(text)
            
            # 보험기간, 납입기간, 가입나이 추출
            terms = self.parse_insurance_terms(sections['block4'])
            
            # 상품 코드 추출
            codes = self.parse_product_codes(sections['block3'])
            
            # 특정 코드의 정보만 반환
            if insu_cd and insu_cd in codes:
                product_name = codes[insu_cd]
                
                # 한도 정보 추출
                limits = self.parse_limits(sections['block5'], product_name)
                
                return {
                    'insuCd': insu_cd,
                    'name': product_name,
                    'terms': terms,
                    'limits': limits,
                    'success': True
                }
            else:
                # 모든 코드 정보 반환
                return {
                    'codes': codes,
                    'terms': terms,
                    'success': True
                }
                
        except Exception as e:
            self.log(f"PDF 파싱 오류: {e}")
            return {'error': str(e), 'success': False}

def main():
    """메인 함수 - Java에서 호출"""
    if len(sys.argv) < 2:
        print(json.dumps({'error': 'PDF 경로가 필요합니다'}, ensure_ascii=False))
        sys.exit(1)
    
    pdf_path = sys.argv[1]
    insu_cd = sys.argv[2] if len(sys.argv) > 2 else None
    
    parser = PDFParser()
    result = parser.parse_pdf(pdf_path, insu_cd)
    
    print(json.dumps(result, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    main()
