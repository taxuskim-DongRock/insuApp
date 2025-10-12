#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
최종 배치 파싱 스크립트 (Phase 3.5)
- 인코딩 문제 해결
- 간편건강보험 코드 추출 개선
- 100% 정확도 목표
"""

import os
import sys
import json
from pathlib import Path

# parse_pdf_improved 모듈 직접 import
sys.path.insert(0, str(Path(__file__).parent))
from parse_pdf_improved import ImprovedPDFParser

def main():
    pdf_dir = Path("C:/insu_app/insuPdf")
    results_dir = Path("C:/insu_app/parse_results")
    results_dir.mkdir(exist_ok=True)
    
    pdf_files = sorted(pdf_dir.glob("UW*.pdf"))
    
    print(f"총 {len(pdf_files)}개의 PDF 파일을 최종 파서로 파싱합니다...\n")
    
    parser = ImprovedPDFParser()
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
        
        try:
            result = parser.parse(str(pdf_file), insu_cd)
            results.append(result)
            
            if result.get('success'):
                terms = result.get('terms', {})
                codes = result.get('codes', {})
                
                has_insu = bool(terms.get('insuTerm'))
                has_pay = bool(terms.get('payTerm'))
                has_age = bool(terms.get('ageRange'))
                has_renew = bool(terms.get('renew'))
                
                # terms가 있으면 성공
                if has_insu or has_pay or has_age:
                    stats['success'] += 1
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
                    print(f"  실패 - terms 없음")
            else:
                stats['failed'] += 1
                print(f"  실패 - {result.get('error', 'Unknown error')}")
        except Exception as e:
            stats['failed'] += 1
            print(f"  실패 - {str(e)}")
            results.append({"error": str(e), "success": False})
    
    # 통계 출력
    print("\n" + "="*60)
    print("최종 파싱 결과 통계 (Phase 3.5)")
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
    csv_path = results_dir / "final_statistics.csv"
    with open(csv_path, 'w', encoding='utf-8') as f:
        f.write("파일명,보험코드,성공,보험기간,납입기간,가입나이,갱신,코드수\n")
        for pdf_file, result in zip(pdf_files, results):
            insu_cd = pdf_file.stem.replace('UW', '')
            success = result.get('success', False)
            terms = result.get('terms', {})
            codes = result.get('codes', {})
            
            # terms가 있으면 성공
            has_terms = bool(terms.get('insuTerm')) or bool(terms.get('payTerm')) or bool(terms.get('ageRange'))
            
            f.write(f"{pdf_file.name},{insu_cd},{success and has_terms},"
                   f"{bool(terms.get('insuTerm'))},"
                   f"{bool(terms.get('payTerm'))},"
                   f"{bool(terms.get('ageRange'))},"
                   f"{bool(terms.get('renew'))},"
                   f"{len(codes)}\n")
    
    print(f"\n통계 파일 저장: {csv_path}")
    
    # JSON 저장
    json_path = results_dir / "final_results.json"
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    
    print(f"결과 파일 저장: {json_path}")

if __name__ == "__main__":
    main()



