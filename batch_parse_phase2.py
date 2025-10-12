#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Phase 2 배치 파싱 스크립트
- Quick Wins + 고급 파서 통합
"""

import os
import json
import subprocess
from pathlib import Path

def parse_pdf(pdf_path, insu_cd):
    """PDF 파싱 (고급 파서 사용)"""
    try:
        result = subprocess.run(
            ['python', 'parse_pdf_advanced.py', pdf_path, insu_cd],
            capture_output=True,
            text=True,
            encoding='utf-8',
            timeout=30
        )
        
        if result.returncode == 0:
            return json.loads(result.stdout)
        else:
            return {"error": result.stderr, "success": False}
    except Exception as e:
        return {"error": str(e), "success": False}

def main():
    pdf_dir = Path("C:/insu_app/insuPdf")
    pdf_files = sorted(pdf_dir.glob("UW*.pdf"))
    
    print(f"총 {len(pdf_files)}개의 PDF 파일을 고급 파서로 파싱합니다...\n")
    
    results = []
    stats = {
        'total': len(pdf_files),
        'success': 0,
        'failed': 0,
        'insuTerm': 0,
        'payTerm': 0,
        'ageRange': 0,
        'renew': 0,
        'total_codes': 0
    }
    
    for idx, pdf_file in enumerate(pdf_files, 1):
        insu_cd = pdf_file.stem.replace('UW', '')
        print(f"[{idx}/{len(pdf_files)}] {pdf_file.name} (코드: {insu_cd}) 파싱 중...")
        
        result = parse_pdf(str(pdf_file), insu_cd)
        results.append(result)
        
        if result.get('success'):
            stats['success'] += 1
            terms = result.get('terms', {})
            codes = result.get('codes', {})
            
            has_insu = bool(terms.get('insuTerm'))
            has_pay = bool(terms.get('payTerm'))
            has_age = bool(terms.get('ageRange'))
            has_renew = bool(terms.get('renew'))
            
            if has_insu:
                stats['insuTerm'] += 1
            if has_pay:
                stats['payTerm'] += 1
            if has_age:
                stats['ageRange'] += 1
            if has_renew:
                stats['renew'] += 1
            
            stats['total_codes'] += len(codes)
            
            print(f"  완료 - 코드: {len(codes)}개, "
                  f"보험기간: {'O' if has_insu else 'X'}, "
                  f"납입기간: {'O' if has_pay else 'X'}, "
                  f"가입나이: {'O' if has_age else 'X'}, "
                  f"갱신: {'O' if has_renew else 'X'}")
        else:
            stats['failed'] += 1
            print(f"  실패 - {result.get('error', 'Unknown error')}")
    
    # 통계 출력
    print("\n" + "="*60)
    print("Phase 2 파싱 결과 통계")
    print("="*60)
    print(f"총 파일 수: {stats['total']}")
    print(f"성공: {stats['success']} ({stats['success']/stats['total']*100:.1f}%)")
    print(f"실패: {stats['failed']} ({stats['failed']/stats['total']*100:.1f}%)")
    print()
    print(f"항목별 성공률:")
    print(f"  - 보험기간: {stats['insuTerm']}/{stats['total']} ({stats['insuTerm']/stats['total']*100:.1f}%)")
    print(f"  - 납입기간: {stats['payTerm']}/{stats['total']} ({stats['payTerm']/stats['total']*100:.1f}%)")
    print(f"  - 가입나이: {stats['ageRange']}/{stats['total']} ({stats['ageRange']/stats['total']*100:.1f}%)")
    print(f"  - 갱신여부: {stats['renew']}/{stats['total']} ({stats['renew']/stats['total']*100:.1f}%)")
    print()
    print(f"총 추출된 상품 코드 수: {stats['total_codes']}")
    print(f"평균 코드 수/파일: {stats['total_codes']/stats['total']:.1f}")
    print("="*60)
    
    # CSV 저장
    csv_path = Path("C:/insu_app/parse_results/phase2_statistics.csv")
    csv_path.parent.mkdir(exist_ok=True)
    
    with open(csv_path, 'w', encoding='utf-8') as f:
        f.write("파일명,보험코드,성공,보험기간,납입기간,가입나이,갱신,코드수\n")
        for pdf_file, result in zip(pdf_files, results):
            insu_cd = pdf_file.stem.replace('UW', '')
            success = result.get('success', False)
            terms = result.get('terms', {})
            codes = result.get('codes', {})
            
            f.write(f"{pdf_file.name},{insu_cd},{success},"
                   f"{bool(terms.get('insuTerm'))},"
                   f"{bool(terms.get('payTerm'))},"
                   f"{bool(terms.get('ageRange'))},"
                   f"{bool(terms.get('renew'))},"
                   f"{len(codes)}\n")
    
    print(f"\n통계 파일 저장: {csv_path}")
    
    # JSON 저장
    json_path = Path("C:/insu_app/parse_results/phase2_results.json")
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    
    print(f"결과 파일 저장: {json_path}")

if __name__ == "__main__":
    main()



