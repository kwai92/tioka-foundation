package com.foundation.notify.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.notify.infrastructure.persistence.AnnouncementDO;
import org.apache.ibatis.annotations.Mapper;

/** 公告头 Mapper（简单 CRUD 走 {@link BaseMapper}，48-CS §4）。 */
@Mapper
public interface AnnouncementMapper extends BaseMapper<AnnouncementDO> {
}
