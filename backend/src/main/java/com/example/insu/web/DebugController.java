// src/main/java/com/example/insu/web/DebugController.java
package com.example.insu.web;

import com.example.insu.util.PdfParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

  @Value("${insu.pdf-dir}")
  private String pdfDir;

  @GetMapping("/pdf-text")
  public String pageText(@RequestParam String file, @RequestParam int page) throws Exception {
    File pdf = new File(pdfDir, file);
    return PdfParser.readPageText(pdf, page);
  }

  @GetMapping("/scan-all")
  public List<Map<String, Object>> scanAll() throws Exception {
    Path dir = Path.of(pdfDir);
    List<Map<String, Object>> out = new ArrayList<>();
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "UW*.pdf")) {
      for (Path p : ds) {
        String text = PdfParser.readAllText(p.toFile());
        PdfParser.Sections sec = PdfParser.splitSections(text);

        Map<String,String> table = PdfParser.parseCodeTable(sec.block3);
        if (table.isEmpty()) table = PdfParser.parseCodes(sec.block3);

        Map<String,String> t = PdfParser.parseTerms(sec.block4);

        out.add(Map.of(
          "file", p.getFileName().toString(),
          "codesFound", table.size(),
          "codesSample", table.keySet().stream().limit(10).collect(Collectors.toList()),
          "termsFound", Map.of(
              "ageRange", t.get("ageRange") != null,
              "insuTerm", t.get("insuTerm") != null,
              "payTerm", t.get("payTerm") != null,
              "renew", t.get("renew") != null,
              "specialNotes", t.get("specialNotes") != null
          )
        ));
      }
    }
    // 파일명 정렬
    out.sort(Comparator.comparing(m -> (String)m.get("file")));
    return out;
  }

  // 특정 코드가 어느 파일에서 발견되는지 빠르게 확인
  @GetMapping("/find-code")
  public Map<String, Object> findCode(@RequestParam String insuCd) throws Exception {
    Path dir = Path.of(pdfDir);
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "UW*.pdf")) {
      for (Path p : ds) {
        String text = PdfParser.readAllText(p.toFile());
        PdfParser.Sections sec = PdfParser.splitSections(text);
        Map<String,String> table = PdfParser.parseCodeTable(sec.block3);
        if (table.containsKey(insuCd)) {
          return Map.of("insuCd", insuCd, "file", p.getFileName().toString(), "name", table.get(insuCd));
        }
      }
    }
    return Map.of("insuCd", insuCd, "file", null, "name", null, "message", "not found in block3");
  }
}
