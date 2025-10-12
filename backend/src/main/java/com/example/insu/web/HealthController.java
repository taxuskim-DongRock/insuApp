package com.example.insu.web;

import com.example.insu.mapper.CommonMapper;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
  private final CommonMapper mapper;
  public HealthController(CommonMapper mapper) { this.mapper = mapper; }

  // 앱만 확인 (DB 미사용)
  @GetMapping("/api/health")
  public String health() { return "UP"; }

  // DB 연결 확인 (기존 ping이 DB를 때린다면 이걸 사용)
  @GetMapping("/api/dbping")
  public String dbping() { 
    // 가장 단순한 쿼리로 테스트
    Integer one = mapper.pingNumber();
    return (one != null && one == 1) ? "DB OK" : "DB FAIL";
  }

  @GetMapping("/ping")
  public Map<String,String> ping() { 
    return Map.of("status","OK"); 
  }
}
