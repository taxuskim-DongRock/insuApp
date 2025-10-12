// src/main/java/com/example/insu/dto/PdfFileDto.java
package com.example.insu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PdfFileDto {
  private String name;
  private long size;
  private Date mtime;
}