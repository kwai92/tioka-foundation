package com.foundation.audit.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.audit.infrastructure.persistence.AuditSealDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 审计链封印 Mapper（F-09 BAS-10）：写入走 {@link BaseMapper}，取链尾用注解 SQL。
 */
@Mapper
public interface AuditSealMapper extends BaseMapper<AuditSealDO> {

    /** 取最近一条封印（链尾），无则返回 {@code null}。 */
    @Select("SELECT * FROM fdn_audit_seal ORDER BY window_to DESC LIMIT 1")
    AuditSealDO latest();
}
