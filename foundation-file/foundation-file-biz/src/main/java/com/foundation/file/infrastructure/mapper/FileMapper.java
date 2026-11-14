package com.foundation.file.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.file.infrastructure.persistence.FileDO;
import org.apache.ibatis.annotations.Mapper;

/** 文件对象 Mapper（F-09 BAS-17）：基础 CRUD 走 {@link BaseMapper}。 */
@Mapper
public interface FileMapper extends BaseMapper<FileDO> {
}
