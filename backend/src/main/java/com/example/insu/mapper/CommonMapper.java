package com.example.insu.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommonMapper {
  @Select("SELECT 1 FROM dual")
  Integer pingNumber();
}
