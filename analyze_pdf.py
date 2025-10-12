#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PDF 텍스트 분석 스크립트
"""

import PyPDF2
import sys
import re

def analyze_pdf(pdf_path):
    try:
        with open(pdf_path, 'rb') as file:
            pdf_reader = PyPDF2.PdfReader(file)
            print(f"PDF 페이지 수: {len(pdf_reader.pages)}")
            
            # 전체 텍스트 추출
            full_text = ""
            for i, page in enumerate(pdf_reader.pages):
                text = page.extract_text()
                full_text += text
                print(f"\n=== 페이지 {i+1} ===")
                print(text[:500])  # 첫 500자만 출력
                print("...")
            
            # 보험기간, 납입기간, 가입나이 관련 텍스트 찾기
            print("\n=== 보험기간 관련 텍스트 ===")
            insu_term_matches = re.findall(r'보험기간[^\n]*', full_text, re.IGNORECASE)
            for match in insu_term_matches:
                print(f"보험기간: {match}")
            
            print("\n=== 납입기간 관련 텍스트 ===")
            pay_term_matches = re.findall(r'납입기간[^\n]*', full_text, re.IGNORECASE)
            for match in pay_term_matches:
                print(f"납입기간: {match}")
            
            print("\n=== 가입나이 관련 텍스트 ===")
            age_matches = re.findall(r'가입나이[^\n]*', full_text, re.IGNORECASE)
            for match in age_matches:
                print(f"가입나이: {match}")
            
            print("\n=== 나이 관련 텍스트 ===")
            age_matches = re.findall(r'[0-9]+세[^\n]*', full_text)
            for match in age_matches:
                print(f"나이: {match}")
            
            print("\n=== 기간 관련 텍스트 ===")
            term_matches = re.findall(r'[0-9]+년[^\n]*', full_text)
            for match in term_matches:
                print(f"기간: {match}")
                
    except Exception as e:
        print(f"오류: {e}")

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("사용법: python analyze_pdf.py <PDF경로>")
        sys.exit(1)
    
    analyze_pdf(sys.argv[1])


