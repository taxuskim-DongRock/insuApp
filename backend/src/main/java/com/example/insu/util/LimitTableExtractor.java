// src/main/java/com/example/insu/util/LimitTableExtractor.java
package com.example.insu.util;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.text.TextPosition;

@Slf4j
public class LimitTableExtractor {

  private static final Pattern P_NUM = Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})*|[0-9]+)\\s*(만|원|백만|천만)?");

  /** 이름 정규화: 공백 제거 + (355)->3N5 치환 등 */
  public static String normalizeName(String s) {
    if (s == null) return "";
    String out = s.replaceAll("\\s+","");
    // (355) → 3N5 치환 규칙 (필요시 확장)
    out = out.replace("355","3N5");
    out = out.replace("다사랑355","다사랑3N5");
    return out;
  }

  /** 헤더 라인에서 열 기준점(xStart) 추출 */
  public static Map<String, Float> detectHeaderAnchors(LayoutStripper.Line headerLine) {
    Map<String,Float> anchors = new LinkedHashMap<>();
    // 헤더 토큰 후보 (필요시 확장)
    String[] tokens = {"60세이하","65세이하","70세이하","80세이하","최저"};
    // 각 토큰의 첫 글자 위치 x
    for (String tk : tokens) {
      int idx = headerLine.text.indexOf(tk);
      if (idx >= 0) {
        float x = xAt(headerLine.positions, idx);
        anchors.put(tk, x);
      }
    }
    return anchors;
  }

  private static float xAt(List<TextPosition> pos, int charIndexInLine) {
    int i = Math.min(charIndexInLine, pos.size()-1);
    return pos.get(i).getXDirAdj();
  }

  /** 해당 행에서, 기준 x좌표로 슬라이스해 수치 추출 */
  public static BigDecimal extractAtBand(LayoutStripper.Line row, String bandKey, Map<String,Float> anchors, String unitHint) {
    if (!anchors.containsKey(bandKey)) return null;

    float fromX = anchors.get(bandKey);
    float toX = Float.MAX_VALUE;
    for (var e : anchors.entrySet()) {
      if (e.getValue() > fromX && e.getValue() < toX) toX = e.getValue();
    }

    // 해당 x구간의 문자만 취합
    StringBuilder sb = new StringBuilder();
    for (TextPosition tp : row.positions) {
      float x = tp.getXDirAdj();
      if (x >= fromX && x < toX) sb.append(tp.getUnicode());
    }
    String slice = sb.toString().replace('\u00A0',' ').trim();
    if (log.isDebugEnabled()) log.debug("[limit][slice] band={} text='{}'", bandKey, slice);

    Matcher m = P_NUM.matcher(slice);
    if (m.find()) {
      String n = m.group(1);
      String u = m.group(2);
      BigDecimal val = new BigDecimal(n.replace(",",""));
      String unit = u != null ? u : unitHint;
      return toWon(val, unit);
    }
    return null;
  }

  private static BigDecimal toWon(BigDecimal v, String unit) {
    if (v == null) return null;
    if (unit == null) return v;
    unit = unit.replaceAll("\\s+","");
    if (unit.contains("천만")) return v.multiply(BigDecimal.valueOf(10_000_000L));
    if (unit.contains("백만")) return v.multiply(BigDecimal.valueOf(1_000_000L));
    if (unit.contains("만"))   return v.multiply(BigDecimal.valueOf(10_000L));
    return v;
  }
}
