package com.example.insu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Slf4j
@Service
public class PythonPdfService {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Python 스크립트를 사용하여 PDF에서 보험기간, 납입기간, 가입나이 정보를 추출
     */
    public Map<String, Object> parsePdfWithPython(String pdfPath, String insuCd) {
        try {
            // Python 스크립트 경로 (개선된 버전 사용)
            String pythonScript = "C:\\insu_app\\parse_pdf_improved.py";
            
            // Python 명령어 구성
            ProcessBuilder processBuilder = new ProcessBuilder(
                "python", pythonScript, pdfPath, insuCd
            );
            
            log.info("Python 명령어 실행: python {} {} {}", pythonScript, pdfPath, insuCd);
            
            // 프로세스 실행
            Process process = processBuilder.start();
            
            // 결과 읽기 (CP949 인코딩으로 변경)
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "CP949")
            );
            
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            
            // 에러 스트림 읽기 (CP949 인코딩으로 변경)
            BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), "CP949")
            );
            
            StringBuilder error = new StringBuilder();
            while ((line = errorReader.readLine()) != null) {
                error.append(line).append("\n");
            }
            
            int exitCode = process.waitFor();
            
            log.info("Python 스크립트 종료 코드: {}", exitCode);
            if (error.length() > 0) {
                log.warn("Python 스크립트 에러 출력: {}", error.toString());
            }
            
            if (exitCode != 0) {
                log.error("Python 스크립트 실행 실패: {}", error.toString());
                return Map.of("error", "Python 스크립트 실행 실패: " + error.toString());
            }
            
            // JSON 결과 파싱
            String jsonResult = result.toString();
            log.info("Python 파싱 결과: {}", jsonResult);
            
            return objectMapper.readValue(jsonResult, Map.class);
            
        } catch (Exception e) {
            log.error("Python PDF 파싱 오류: {}", e.getMessage(), e);
            return Map.of("error", "Python PDF 파싱 오류: " + e.getMessage());
        }
    }
    
    /**
     * PDF 파일에서 특정 보험 코드의 정보를 추출
     */
    public Map<String, Object> extractProductInfo(String pdfPath, String insuCd) {
        try {
            File pdfFile = new File(pdfPath);
            if (!pdfFile.exists()) {
                return Map.of("error", "PDF 파일이 존재하지 않습니다: " + pdfPath);
            }
            
            return parsePdfWithPython(pdfPath, insuCd);
            
        } catch (Exception e) {
            log.error("상품 정보 추출 오류: {}", e.getMessage(), e);
            return Map.of("error", "상품 정보 추출 오류: " + e.getMessage());
        }
    }
}
