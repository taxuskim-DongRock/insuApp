#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Parse UW21239.pdf to extract:
#  - Section 3. 보험코드 -> product_name <-> code(s)
#  - Section 5. 가입한도 -> product_name <-> min/max/unit
# Then upsert into Oracle 18c tables defined in uw_schema_oracle.sql.
#
# Requirements:
#   pip install pdfminer.six oracledb
#
# Usage:
#   export ORACLE_DSN="host:port/service"
#   export ORACLE_USER="DEVOWN"
#   export ORACLE_PASS="***"
#   python parse_uw21239_to_oracle.py /path/to/UW21239.pdf
#
# Notes:
#  - The regexes are tailored to UW21239.pdf structure observed in the provided excerpt.
#  - For other U/W PDFs, you may need to extend PATTERNS below.

import os
import re
import sys
from typing import Dict, List, Tuple, Optional

try:
    from pdfminer.high_level import extract_text
except Exception as e:
    print("ERROR: pdfminer.six is required: pip install pdfminer.six")
    raise

def normalize_spaces(s: str) -> str:
    import re as _re
    return _re.sub(r"\s+", " ", s).strip()

def to_number(money_str: str) -> Optional[int]:
    s = money_str.replace(",", "").strip()
    units = {"천": 1000000000000, "억": 100000000, "만": 10000}
    m = re.match(r"^(\d+)(천|억|만)$", s)
    if m:
        val, unit = m.groups()
        base = int(val) * units[unit]
        return base
    if s.isdigit():
        return int(s)
    return None

def split_sections(text: str) -> Dict[str, str]:
    sects = {}
    m3 = re.search(r"\n\s*3\.\s*보험코드\s*\n", text)
    m4 = re.search(r"\n\s*4\.\s*사업방법\s*\n", text)
    m5 = re.search(r"\n\s*5\.\s*가입한도\s*\n", text)
    m6 = re.search(r"\n\s*6\.\s*선택특약 입력시 주의사항\s*\n", text)

    if m3 and m4:
        sects["codes"] = text[m3.end():m4.start()]
    if m5 and m6:
        sects["limits"] = text[m5.end():m6.start()]
    return sects

PAT_CODE_SIMPLE = re.compile(r"""
    ^\s*
    (?P<name>\(.+?\)|[^\d\n]+?)          
    \s+
    (?P<code_v2>\d{5})                   
    \s+
    (?P<code_std>\d{5})                  
    \s*$
""", re.VERBOSE)

PAT_CODE_GYEONGSIN_2 = re.compile(r"""
    ^\s*
    (?P<name>.+?)
    \s*
    \(\s*갱신형,\s*(?P<term>\d+년)\s*\)
    \s*
    (?P<renewal>최초계약|갱신계약)
    \s*
    (?P<code>\d{5})
    \s*$
""", re.VERBOSE)

PAT_CODE_GYEONGSIN_TERMONLY = re.compile(r"""
    ^\s*
    (?P<name>.+?)
    \s*
    \(\s*갱신형\s*\)
    \s*
    (?P<renewal>최초계약|갱신계약)
    \s*
    (?P<code>\d{5})
    \s*$
""", re.VERBOSE)

PAT_LIMIT = re.compile(r"""
    ^\s*
    (?P<name>[^\s].*?[^\s])
    \s+
    (?P<max>[0-9,]+[천억만]?)
    \s+
    (?P<min>[0-9,]+[천억만]?)
    \s+
    (?P<unit>[0-9,]+[천억만]?)
    \s*$
""", re.VERBOSE)

def parse_codes(sect_text: str) -> Dict[str, List[Tuple[Optional[str], Optional[str], Optional[str], str]]]:
    result: Dict[str, List[Tuple[Optional[str], Optional[str], Optional[str], str]]] = {}
    for raw in sect_text.splitlines():
        line = normalize_spaces(raw)
        if not line:
            continue
        m = PAT_CODE_SIMPLE.match(line)
        if m:
            name = m.group("name").strip()
            v2 = m.group("code_v2")
            std = m.group("code_std")
            result.setdefault(name, []).append(("해약환급금미지급형V2", None, None, v2))
            result.setdefault(name, []).append(("표준형", None, None, std))
            continue
        m = PAT_CODE_GYEONGSIN_2.match(line) or PAT_CODE_GYEONGSIN_TERMONLY.match(line)
        if m:
            name = m.group("name").strip()
            term = m.groupdict().get("term")
            renewal = m.group("renewal")
            code = m.group("code")
            result.setdefault(name, []).append(("갱신형(표준형)", renewal, term, code))
            continue
    return result

def parse_limits(sect_text: str) -> Dict[str, Tuple[Optional[int], Optional[int], str]]:
    limits = {}
    for raw in sect_text.splitlines():
        line = normalize_spaces(raw)
        if not line:
            continue
        m = PAT_LIMIT.match(line)
        if m:
            name = m.group("name").strip()
            max_amt = to_number(m.group("max"))
            min_amt = to_number(m.group("min"))
            unit = m.group("unit")
            limits[name] = (max_amt, min_amt, unit)
    return limits

def get_conn():
    import oracledb
    dsn = os.environ.get("ORACLE_DSN")
    user = os.environ.get("ORACLE_USER")
    pw = os.environ.get("ORACLE_PASS")
    if not all([dsn, user, pw]):
        raise RuntimeError("Please set ORACLE_DSN, ORACLE_USER, ORACLE_PASS environment variables.")
    conn = oracledb.connect(user=user, password=pw, dsn=dsn)
    return conn

def ensure_product(cur, name: str, contract_type: str = '특약') -> int:
    cur.execute("SELECT PRODUCT_ID FROM UW_PRODUCT WHERE PRODUCT_NAME = :1", (name,))
    r = cur.fetchone()
    if r:
        return int(r[0])
    # Oracle RETURNING with oracledb
    id_var = cur.var(oracledb.NUMBER)
    cur.execute("INSERT INTO UW_PRODUCT(PRODUCT_NAME, CONTRACT_TYPE) VALUES(:1, :2) RETURNING PRODUCT_ID INTO :3",
                (name, contract_type, id_var))
    return int(id_var.getvalue())

def upsert_codes(cur, product_id: int, entries: List[Tuple[Optional[str], Optional[str], Optional[str], str]]):
    for variant, renewal, term, code in entries:
        cur.execute("""
            SELECT 1 FROM UW_PRODUCT_CODE 
             WHERE PRODUCT_ID=:1 AND NVL(VARIANT,'-')=NVL(:2,'-') AND NVL(RENEWAL_TAG,'-')=NVL(:3,'-')
               AND NVL(TERM_TAG,'-')=NVL(:4,'-') AND INSU_CD=:5
        """, (product_id, variant, renewal, term, code))
        if cur.fetchone():
            continue
        cur.execute("""
            INSERT INTO UW_PRODUCT_CODE(PRODUCT_ID, VARIANT, RENEWAL_TAG, TERM_TAG, INSU_CD)
            VALUES(:1, :2, :3, :4, :5)
        """, (product_id, variant, renewal, term, code))

def upsert_limit(cur, product_id: int, limit_row: Tuple[Optional[int], Optional[int], str]):
    max_amt, min_amt, unit = limit_row
    cur.execute("SELECT LIMIT_ID FROM UW_PRODUCT_LIMIT WHERE PRODUCT_ID=:1", (product_id,))
    r = cur.fetchone()
    if r:
        cur.execute("""
            UPDATE UW_PRODUCT_LIMIT
               SET LIMIT_MAX_AMT=:2, LIMIT_MIN_AMT=:3, LIMIT_UNIT=:4
             WHERE LIMIT_ID=:1
        """, (r[0], max_amt, min_amt, unit))
    else:
        cur.execute("""
            INSERT INTO UW_PRODUCT_LIMIT(PRODUCT_ID, LIMIT_MAX_AMT, LIMIT_MIN_AMT, LIMIT_UNIT)
            VALUES(:1, :2, :3, :4)
        """, (product_id, max_amt, min_amt, unit))

def main(pdf_path: str):
    full_text = extract_text(pdf_path)
    sections = split_sections(full_text)
    codes_text = sections.get("codes", "")
    limits_text = sections.get("limits", "")

    code_map = parse_codes(codes_text)
    limit_map = parse_limits(limits_text)

    conn = get_conn()
    cur = conn.cursor()

    MAIN_CONTRACT_KEYWORDS = ["주계약", "다사랑암보험"]

    for name, code_entries in code_map.items():
        contract_type = '주계약' if any(k in name for k in MAIN_CONTRACT_KEYWORDS) else '특약'
        pid = ensure_product(cur, name, contract_type)
        upsert_codes(cur, pid, code_entries)
        if name in limit_map:
            upsert_limit(cur, pid, limit_map[name])

    for name, lim in limit_map.items():
        if name not in code_map:
            pid = ensure_product(cur, name, '특약')
            upsert_limit(cur, pid, lim)

    conn.commit()
    cur.close()
    conn.close()
    print("Done. Loaded products, codes, and limits into Oracle.")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python parse_uw21239_to_oracle.py /path/to/UW21239.pdf")
        sys.exit(2)
    main(sys.argv[1])
