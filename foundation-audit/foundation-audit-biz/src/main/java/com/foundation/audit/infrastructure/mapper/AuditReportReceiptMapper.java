package com.foundation.audit.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.audit.infrastructure.persistence.AuditReportReceiptDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 上报批次回执 Mapper（F-09 BAS-10）：全部走 {@link BaseMapper} 的 CRUD。
 */
@Mapper
public interface AuditReportReceiptMapper extends BaseMapper<AuditReportReceiptDO> {
}
