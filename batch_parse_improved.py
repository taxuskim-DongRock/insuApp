#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
개선된 일괄 PDF 파싱 스크립트
"""

import os
import json
import sys
from pathlib import Path
import subprocess
from datetime import datetime

class BatchImprovedParser:
    def __init__(self, pdf_dir, output_dir):
        self.pdf_dir = Path(pdf_dir)
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(exist_ok=True)
        
        # 결과 저장 디렉토리
        self.results_dir = self.output_dir / "improved_results"
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
        """파일명에서 보험코드 추출"""
        name = filename.replace("UW", "").replace(".pdf", "")
        return name
    
    def parse_with_improved(self, pdf_path, insu_cd):
        """개선된 파서로 파싱 (Phase 3.5: 인코딩 개선)"""
        try:
            result = subprocess.run(
                ["python", "parse_pdf_improved.py", str(pdf_path), insu_cd],
                capture_output=True,
                text=True,
                encoding='utf-8',
                errors='ignore',  # 인코딩 에러 무시
                timeout=30
            )
            
            if result.returncode == 0:
                # stderr 제거 (디버그 메시지) 및 JSON만 추출
                stdout_lines = result.stdout.split('\n')
                
                # JSON 시작 찾기 ('{' 로 시작하는 첫 줄)
                json_start = -1
                for i, line in enumerate(stdout_lines):
                    if line.strip().startswith('{'):
                        json_start = i
                        break
                
                if json_start >= 0:
                    json_str = '\n'.join(stdout_lines[json_start:])
                    return json.loads(json_str)
                else:
                    return {"error": "JSON not found in output", "success": False}
            else:
                return {"error": result.stderr, "success": False}
        except Exception as e:
            return {"error": str(e), "success": False}
    
    def analyze_result(self, result, insu_cd):
        """파싱 결과 분석 (Phase 3: 개선)"""
        # terms가 있으면 성공으로 판단 (코드가 0개여도 OK)
        has_terms = any([
            result.get("terms", {}).get("insuTerm"),
            result.get("terms", {}).get("payTerm"),
            result.get("terms", {}).get("ageRange")
        ])
        
        analysis = {
            "insuCd": insu_cd,
            "success": result.get("success", False) or has_terms,
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
        
        print(f"총 {len(pdf_files)}개의 PDF 파일을 개선된 파서로 파싱합니다...\n")
        
        for idx, pdf_file in enumerate(pdf_files, 1):
            insu_cd = self.extract_insu_code(pdf_file.name)
            print(f"[{idx}/{len(pdf_files)}] {pdf_file.name} (코드: {insu_cd}) 파싱 중...")
            
            # 개선된 파서로 파싱
            result = self.parse_with_improved(pdf_file, insu_cd)
            
            # 결과 분석
            analysis = self.analyze_result(result, insu_cd)
            
            # 결과 저장
            output_file = self.results_dir / f"{insu_cd}_improved.json"
            with open(output_file, 'w', encoding='utf-8') as f:
                json.dump(result, f, ensure_ascii=False, indent=2)
            
            # 진행 상황 출력
            print(f"  완료 - 코드: {analysis['codes_count']}개, "
                  f"보험기간: {'O' if analysis['terms']['insuTerm'] else 'X'}, "
                  f"납입기간: {'O' if analysis['terms']['payTerm'] else 'X'}, "
                  f"가입나이: {'O' if analysis['terms']['ageRange'] else 'X'}, "
                  f"갱신: {'O' if analysis['terms']['renew'] else 'X'}")
            
            self.stats["files"].append({
                "filename": pdf_file.name,
                "insuCd": insu_cd,
                "analysis": analysis
            })
        
        # 최종 통계 저장
        self.save_statistics()
        self.print_summary()
        self.print_comparison()
    
    def save_statistics(self):
        """통계 정보 저장"""
        stats_file = self.output_dir / "improved_statistics.json"
        with open(stats_file, 'w', encoding='utf-8') as f:
            json.dump(self.stats, f, ensure_ascii=False, indent=2)
        
        # CSV 형식으로도 저장
        csv_file = self.output_dir / "improved_statistics.csv"
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
        print("개선된 파서 결과 요약")
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
    
    def print_comparison(self):
        """이전 결과와 비교"""
        print("\n" + "="*60)
        print("이전 결과 대비 개선율")
        print("="*60)
        
        # 이전 결과 (하드코딩)
        old_stats = {
            "insuTerm": 9,
            "payTerm": 9,
            "ageRange": 9,
            "renew": 2
        }
        
        for key in old_stats:
            old_val = old_stats[key]
            new_val = self.stats['terms_extracted'][key]
            improvement = new_val - old_val
            improvement_pct = (improvement / self.stats['total']) * 100
            
            print(f"{key}: {old_val} -> {new_val} (+{improvement}, +{improvement_pct:.1f}%p)")
        
        print("="*60)

if __name__ == "__main__":
    pdf_dir = r"C:\insu_app\insuPdf"
    output_dir = r"C:\insu_app\parse_results"
    
    parser = BatchImprovedParser(pdf_dir, output_dir)
    parser.parse_all()

