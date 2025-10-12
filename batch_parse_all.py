#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
일괄 PDF 파싱 스크립트
모든 PDF 파일을 파싱하고 결과를 분석합니다.
"""

import os
import json
import sys
from pathlib import Path
import subprocess
from datetime import datetime

class BatchPDFParser:
    def __init__(self, pdf_dir, output_dir):
        self.pdf_dir = Path(pdf_dir)
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(exist_ok=True)
        
        # 결과 저장 디렉토리
        self.results_dir = self.output_dir / "results"
        self.results_dir.mkdir(exist_ok=True)
        
        # 통계 정보
        self.stats = {
            "total": 0,
            "success": 0,
            "failed": 0,
            "terms_extracted": {
                "insuTerm": 0,
                "payTerm": 0,
                "ageRange": 0,
                "renew": 0
            },
            "codes_extracted": 0,
            "files": []
        }
    
    def extract_insu_code(self, filename):
        """파일명에서 보험코드 추출 (예: UW21239.pdf -> 21239)"""
        name = filename.replace("UW", "").replace(".pdf", "")
        return name
    
    def parse_with_python(self, pdf_path, insu_cd):
        """Python 스크립트로 파싱"""
        try:
            result = subprocess.run(
                ["python", "parse_pdf_python.py", str(pdf_path), insu_cd],
                capture_output=True,
                text=True,
                timeout=30
            )
            
            if result.returncode == 0:
                return json.loads(result.stdout)
            else:
                return {"error": result.stderr, "success": False}
        except Exception as e:
            return {"error": str(e), "success": False}
    
    def parse_with_ocr(self, pdf_path, insu_cd):
        """OCR 스크립트로 파싱"""
        try:
            result = subprocess.run(
                ["python", "parse_pdf_ocr.py", str(pdf_path), insu_cd],
                capture_output=True,
                text=True,
                timeout=60
            )
            
            if result.returncode == 0:
                return json.loads(result.stdout)
            else:
                return {"error": result.stderr, "success": False}
        except Exception as e:
            return {"error": str(e), "success": False}
    
    def analyze_result(self, result, insu_cd):
        """파싱 결과 분석"""
        analysis = {
            "insuCd": insu_cd,
            "success": result.get("success", False),
            "method": result.get("method", "unknown"),
            "terms": {
                "insuTerm": bool(result.get("terms", {}).get("insuTerm")),
                "payTerm": bool(result.get("terms", {}).get("payTerm")),
                "ageRange": bool(result.get("terms", {}).get("ageRange")),
                "renew": bool(result.get("terms", {}).get("renew"))
            },
            "codes_count": len(result.get("codes", {})),
            "has_error": "error" in result
        }
        
        # 통계 업데이트
        if analysis["success"]:
            self.stats["success"] += 1
        else:
            self.stats["failed"] += 1
        
        if analysis["terms"]["insuTerm"]:
            self.stats["terms_extracted"]["insuTerm"] += 1
        if analysis["terms"]["payTerm"]:
            self.stats["terms_extracted"]["payTerm"] += 1
        if analysis["terms"]["ageRange"]:
            self.stats["terms_extracted"]["ageRange"] += 1
        if analysis["terms"]["renew"]:
            self.stats["terms_extracted"]["renew"] += 1
        
        self.stats["codes_extracted"] += analysis["codes_count"]
        
        return analysis
    
    def parse_all(self):
        """모든 PDF 파일 파싱"""
        pdf_files = sorted(self.pdf_dir.glob("*.pdf"))
        self.stats["total"] = len(pdf_files)
        
        print(f"총 {len(pdf_files)}개의 PDF 파일을 파싱합니다...\n")
        
        for idx, pdf_file in enumerate(pdf_files, 1):
            insu_cd = self.extract_insu_code(pdf_file.name)
            print(f"[{idx}/{len(pdf_files)}] {pdf_file.name} (코드: {insu_cd}) 파싱 중...")
            
            # 1. Python 기본 파싱 시도
            result_python = self.parse_with_python(pdf_file, insu_cd)
            
            # 2. OCR 파싱 시도
            result_ocr = self.parse_with_ocr(pdf_file, insu_cd)
            
            # 3. 결과 결합 (OCR 우선)
            combined_result = {
                "insuCd": insu_cd,
                "filename": pdf_file.name,
                "python": result_python,
                "ocr": result_ocr,
                "timestamp": datetime.now().isoformat()
            }
            
            # 더 나은 결과 선택
            if result_ocr.get("success") and len(result_ocr.get("codes", {})) > 0:
                best_result = result_ocr
            elif result_python.get("success"):
                best_result = result_python
            else:
                best_result = result_ocr
            
            combined_result["best"] = best_result
            
            # 결과 분석
            analysis = self.analyze_result(best_result, insu_cd)
            combined_result["analysis"] = analysis
            
            # 결과 저장
            output_file = self.results_dir / f"{insu_cd}_result.json"
            with open(output_file, 'w', encoding='utf-8') as f:
                json.dump(combined_result, f, ensure_ascii=False, indent=2)
            
            # 진행 상황 출력
            print(f"  완료 - 코드: {analysis['codes_count']}개, "
                  f"보험기간: {'O' if analysis['terms']['insuTerm'] else 'X'}, "
                  f"납입기간: {'O' if analysis['terms']['payTerm'] else 'X'}, "
                  f"가입나이: {'O' if analysis['terms']['ageRange'] else 'X'}")
            
            self.stats["files"].append({
                "filename": pdf_file.name,
                "insuCd": insu_cd,
                "analysis": analysis
            })
        
        # 최종 통계 저장
        self.save_statistics()
        self.print_summary()
    
    def save_statistics(self):
        """통계 정보 저장"""
        stats_file = self.output_dir / "statistics.json"
        with open(stats_file, 'w', encoding='utf-8') as f:
            json.dump(self.stats, f, ensure_ascii=False, indent=2)
        
        # CSV 형식으로도 저장
        csv_file = self.output_dir / "statistics.csv"
        with open(csv_file, 'w', encoding='utf-8') as f:
            f.write("파일명,보험코드,성공,보험기간,납입기간,가입나이,갱신,코드수\n")
            for file_info in self.stats["files"]:
                analysis = file_info["analysis"]
                f.write(f"{file_info['filename']},{file_info['insuCd']},"
                       f"{analysis['success']},{analysis['terms']['insuTerm']},"
                       f"{analysis['terms']['payTerm']},{analysis['terms']['ageRange']},"
                       f"{analysis['terms']['renew']},{analysis['codes_count']}\n")
    
    def print_summary(self):
        """요약 정보 출력"""
        print("\n" + "="*60)
        print("파싱 결과 요약")
        print("="*60)
        print(f"총 파일 수: {self.stats['total']}")
        print(f"성공: {self.stats['success']} ({self.stats['success']/self.stats['total']*100:.1f}%)")
        print(f"실패: {self.stats['failed']} ({self.stats['failed']/self.stats['total']*100:.1f}%)")
        print("\n추출된 정보:")
        print(f"  - 보험기간: {self.stats['terms_extracted']['insuTerm']}/{self.stats['total']} "
              f"({self.stats['terms_extracted']['insuTerm']/self.stats['total']*100:.1f}%)")
        print(f"  - 납입기간: {self.stats['terms_extracted']['payTerm']}/{self.stats['total']} "
              f"({self.stats['terms_extracted']['payTerm']/self.stats['total']*100:.1f}%)")
        print(f"  - 가입나이: {self.stats['terms_extracted']['ageRange']}/{self.stats['total']} "
              f"({self.stats['terms_extracted']['ageRange']/self.stats['total']*100:.1f}%)")
        print(f"  - 갱신여부: {self.stats['terms_extracted']['renew']}/{self.stats['total']} "
              f"({self.stats['terms_extracted']['renew']/self.stats['total']*100:.1f}%)")
        print(f"\n총 추출된 상품 코드 수: {self.stats['codes_extracted']}")
        print(f"평균 코드 수/파일: {self.stats['codes_extracted']/self.stats['total']:.1f}")
        print("="*60)
        print(f"\n결과 저장 위치: {self.output_dir}")
        print(f"  - 개별 결과: {self.results_dir}")
        print(f"  - 통계 정보: {self.output_dir / 'statistics.json'}")
        print(f"  - CSV 파일: {self.output_dir / 'statistics.csv'}")

if __name__ == "__main__":
    pdf_dir = r"C:\insu_app\insuPdf"
    output_dir = r"C:\insu_app\parse_results"
    
    parser = BatchPDFParser(pdf_dir, output_dir)
    parser.parse_all()

