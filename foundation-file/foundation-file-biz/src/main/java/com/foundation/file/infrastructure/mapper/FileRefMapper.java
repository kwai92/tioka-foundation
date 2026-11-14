package com.foundation.file.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.file.infrastructure.persistence.FileRefDO;
import org.apache.ibatis.annotations.Mapper;

/** 文件引用 Mapper（F-09 BAS-17）：基础 CRUD 走 {@link BaseMapper}。 */
@Mapper
public interface FileRefMapper extends BaseMapper<FileRefDO> {
}
