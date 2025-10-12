#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PDF 텍스트 디버깅 스크립트
"""

import PyPDF2
import sys

def debug_pdf(pdf_path):
    try:
        with open(pdf_path, 'rb') as file:
            pdf_reader = PyPDF2.PdfReader(file)
            print(f"PDF 페이지 수: {len(pdf_reader.pages)}")
            
            for i, page in enumerate(pdf_reader.pages):
                print(f"\n=== 페이지 {i+1} ===")
                text = page.extract_text()
                print(text[:1000])  # 첫 1000자만 출력
                print("...")
                
    except Exception as e:
        print(f"오류: {e}")

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("사용법: python debug_pdf.py <PDF경로>")
        sys.exit(1)
    
    debug_pdf(sys.argv[1])

