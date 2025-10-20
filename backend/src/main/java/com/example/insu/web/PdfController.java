// src/main/java/com/example/insu/web/PdfController.java
package com.example.insu.web;

import com.example.insu.dto.CodeEntryDto;
import com.example.insu.dto.PdfFileDto;
import com.example.insu.service.PdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
public class PdfController {

  @Value("${insu.pdf-dir}")
  private String pdfDir;

  private final PdfService pdfService;

  /** PDF 파일 리스트 */
  @GetMapping("/list")
  public List<PdfFileDto> list() {
    return pdfService.listPdfs(Path.of(pdfDir));
  }

  /** 코드/명칭 목록 (type=main: 최초만, rider: 전체) */
  @GetMapping("/codes")
  public List<CodeEntryDto> codes(@RequestParam String file,
                                  @RequestParam(defaultValue = "main") String type) throws Exception {
    log.info("===== PDF 코드 조회 API 호출 =====");
    log.info("요청 파일: {}", file);
    log.info("요청 타입: {}", type);
    
    try {
      Path pdfPath = Path.of(pdfDir).resolve(file);
      log.info("PDF 파일 경로: {}", pdfPath);
      log.info("PDF 파일 존재 여부: {}", pdfPath.toFile().exists());
      
      List<CodeEntryDto> result = pdfService.codes(pdfPath, type);
      log.info("조회된 코드 개수: {}", result != null ? result.size() : 0);
      
      if (result != null && !result.isEmpty()) {
        log.info("조회된 코드 목록:");
        for (CodeEntryDto code : result) {
          log.info("  - {}: {}", code.getInsuCd(), code.getName());
        }
      } else {
        log.warn("조회된 코드가 없음. 파일: {}, 타입: {}", file, type);
      }
      
      log.info("===== PDF 코드 조회 API 완료 =====");
      return result;
      
    } catch (Exception e) {
      log.error("PDF 코드 조회 중 오류 발생", e);
      log.error("오류 상세 - 파일: {}, 타입: {}, 오류: {}", file, type, e.getMessage());
      throw e;
    }
  }


}


