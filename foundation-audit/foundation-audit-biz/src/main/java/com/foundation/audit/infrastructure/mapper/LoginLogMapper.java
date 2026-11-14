package com.foundation.audit.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.audit.infrastructure.persistence.LoginLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 登录日志 Mapper（F-09 BAS-10）：插入走 {@link BaseMapper}，分页 / 计数 / 失败统计用注解 SQL。
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLogDO> {

    /** 分页查询（按 {@code (ts, id)} 倒序）。 */
    @Select("""
            <script>
            SELECT * FROM fdn_login_log
            WHERE 1 = 1
            <if test="loginName != null"> AND login_name = #{loginName}</if>
            <if test="personId != null"> AND person_id = #{personId}</if>
            <if test="success != null"> AND success = #{success}</if>
            <if test="from != null"> AND ts &gt;= #{from}</if>
            <if test="to != null"> AND ts &lt; #{to}</if>
            ORDER BY ts DESC, id DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<LoginLogDO> page(@Param("loginName") String loginName, @Param("personId") String personId,
                          @Param("success") Boolean success, @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to, @Param("size") int size, @Param("offset") long offset);

    /** 同条件计数。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM fdn_login_log
            WHERE 1 = 1
            <if test="loginName != null"> AND login_name = #{loginName}</if>
            <if test="personId != null"> AND person_id = #{personId}</if>
            <if test="success != null"> AND success = #{success}</if>
            <if test="from != null"> AND ts &gt;= #{from}</if>
            <if test="to != null"> AND ts &lt; #{to}</if>
            </script>
            """)
    long count(@Param("loginName") String loginName, @Param("personId") String personId,
               @Param("success") Boolean success, @Param("from") LocalDateTime from,
               @Param("to") LocalDateTime to);

    /**
     * 失败统计：按登录名聚合失败次数。
     *
     * <p>返回 {@code login_name} / {@code failures} 两列；由仓储手工转成契约 record，避免 record 构造映射对本
     * SQL 的额外约束。
     */
    @Select("""
            <script>
            SELECT login_name AS login_name, COUNT(*) AS failures FROM fdn_login_log
            WHERE success = FALSE
            <if test="loginName != null"> AND login_name = #{loginName}</if>
            <if test="from != null"> AND ts &gt;= #{from}</if>
            <if test="to != null"> AND ts &lt; #{to}</if>
            GROUP BY login_name ORDER BY failures DESC
            </script>
            """)
    List<Map<String, Object>> failureStats(@Param("loginName") String loginName,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);
}
