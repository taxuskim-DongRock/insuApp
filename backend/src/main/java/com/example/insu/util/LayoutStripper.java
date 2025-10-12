// src/main/java/com/example/insu/util/LayoutStripper.java
package com.example.insu.util;

import java.io.IOException;
import java.util.*;
import lombok.Getter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/** PDFBox 2.x 호환: 각 라인별로 (문자열, xStart, yBaseline) 수집 */
public class LayoutStripper extends PDFTextStripper {

  @Getter
  public static class Line {
    public final String text;
    public final float y;
    public final List<TextPosition> positions;
    public Line(String text, float y, List<TextPosition> pos) {
      this.text = text; this.y = y; this.positions = pos;
    }
  }

  private final List<Line> lines = new ArrayList<>();
  private final Map<Integer,List<Line>> pageLines = new HashMap<>();
  private final List<TextPosition> current = new ArrayList<>();
  private float currentY = -1f;

  public LayoutStripper() throws IOException {
    setSortByPosition(true);
  }

  @Override
  protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
    if (text == null || text.isEmpty()) return;
    // 같은 텍스트 조각을 한 라인으로 취급
    float y = Math.round(textPositions.get(0).getY());
    if (currentY != -1f && Math.abs(y - currentY) > 1.0f) {
      flushLine();
    }
    current.addAll(textPositions);
    currentY = y;
    super.writeString(text, textPositions);
  }

  @Override
  protected void writeLineSeparator() throws IOException {
    flushLine();
    super.writeLineSeparator();
  }

  @Override
  protected void startPage(PDPage page) throws IOException {
    super.startPage(page);
    lines.clear();
    currentY = -1f;      // 또는 null 초기화
  }
  
  @Override
  protected void endPage(PDPage page) throws IOException {
    flushLine();
    // 페이지별 라인 저장 (getCurrentPageNo()는 1-based)
    pageLines.put(getCurrentPageNo(), new ArrayList<>(lines));
    lines.clear();
    currentY = -1f;      // 또는 null 사용하셔도 됩니다
    super.endPage(page);
  }

  private void flushLine() {
    if (current.isEmpty()) return;
    StringBuilder sb = new StringBuilder();
    for (TextPosition tp : current) sb.append(tp.getUnicode());
    String s = sb.toString().replace('\u00A0',' ').trim();
    if (!s.isEmpty()) {
      lines.add(new Line(s, current.get(0).getY(), new ArrayList<>(current)));
    }
    current.clear();
  }

  public static List<Line> readPageLines(java.io.File pdf, int pageIndex1Based) throws IOException {
    try (PDDocument doc = PDDocument.load(pdf)) {
      LayoutStripper st = new LayoutStripper();
      st.setStartPage(pageIndex1Based);
      st.setEndPage(pageIndex1Based);
      st.getText(doc); // 수집 트리거
      return st.pageLines.getOrDefault(pageIndex1Based, List.of());
    }
  }
}
