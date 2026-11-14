package com.foundation.audit.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.audit.infrastructure.persistence.AuditLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 审计行 Mapper（F-09 BAS-10）：常规插入走 {@link BaseMapper}，条件分页 / 导出 / 窗口扫描用注解 SQL。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogDO> {

    /** 条件游标分页：按 {@code (ts, id)} 倒序，游标取上一页末行。 */
    @Select("""
            <script>
            SELECT * FROM fdn_audit_log
            WHERE 1 = 1
            <if test="apps != null"> AND app_code IN <foreach collection="apps" item="a" open="(" separator="," close=")">#{a}</foreach></if>
            <if test="actorId != null"> AND actor_id = #{actorId}</if>
            <if test="action != null"> AND action = #{action}</if>
            <if test="resourceType != null"> AND resource_type = #{resourceType}</if>
            <if test="resourceId != null"> AND resource_id = #{resourceId}</if>
            <if test="outcome != null"> AND outcome = #{outcome}</if>
            <if test="from != null"> AND ts &gt;= #{from}</if>
            <if test="to != null"> AND ts &lt; #{to}</if>
            <if test="cursorTs != null and cursorId != null"> AND (ts, id) &lt; (#{cursorTs}, #{cursorId})</if>
            ORDER BY ts DESC, id DESC LIMIT #{size}
            </script>
            """)
    List<AuditLogDO> page(@Param("apps") Collection<String> apps, @Param("actorId") String actorId,
                          @Param("action") String action, @Param("resourceType") String resourceType,
                          @Param("resourceId") String resourceId, @Param("outcome") String outcome,
                          @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                          @Param("cursorTs") LocalDateTime cursorTs, @Param("cursorId") String cursorId,
                          @Param("size") int size);

    /** 导出：同条件取全量（无游标），以 {@code limit} 控制上限。 */
    @Select("""
            <script>
            SELECT * FROM fdn_audit_log
            WHERE 1 = 1
            <if test="apps != null"> AND app_code IN <foreach collection="apps" item="a" open="(" separator="," close=")">#{a}</foreach></if>
            <if test="actorId != null"> AND actor_id = #{actorId}</if>
            <if test="action != null"> AND action = #{action}</if>
            <if test="resourceType != null"> AND resource_type = #{resourceType}</if>
            <if test="resourceId != null"> AND resource_id = #{resourceId}</if>
            <if test="outcome != null"> AND outcome = #{outcome}</if>
            <if test="from != null"> AND ts &gt;= #{from}</if>
            <if test="to != null"> AND ts &lt; #{to}</if>
            ORDER BY ts DESC, id DESC LIMIT #{limit}
            </script>
            """)
    List<AuditLogDO> listForExport(@Param("apps") Collection<String> apps, @Param("actorId") String actorId,
                                   @Param("action") String action, @Param("resourceType") String resourceType,
                                   @Param("resourceId") String resourceId, @Param("outcome") String outcome,
                                   @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                   @Param("limit") int limit);

    /** 封印用窗口扫描：按 {@code [from, to)} 取行并按 {@code id} 升序（不做分区过滤）。 */
    @Select("SELECT * FROM fdn_audit_log WHERE ts >= #{from} AND ts < #{to} ORDER BY id")
    List<AuditLogDO> listByWindow(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
