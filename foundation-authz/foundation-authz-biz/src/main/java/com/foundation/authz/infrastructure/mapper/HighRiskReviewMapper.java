package com.foundation.authz.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.authz.infrastructure.persistence.HighRiskReviewDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 高风险授权复核 Mapper（F-09 BAS-19）：插入走 {@link BaseMapper}，状态游标分页用注解 SQL。 */
@Mapper
public interface HighRiskReviewMapper extends BaseMapper<HighRiskReviewDO> {

    /** 状态游标分页：按 {@code (create_at, id)} 倒序，游标取上一页末行。 */
    @Select("""
            <script>
            SELECT * FROM fdn_high_risk_review
            WHERE deleted = FALSE
            <if test="status != null"> AND status = #{status}</if>
            <if test="cursorTs != null and cursorId != null"> AND (create_at, id) &lt; (#{cursorTs}, #{cursorId})</if>
            ORDER BY create_at DESC, id DESC LIMIT #{size}
            </script>
            """)
    List<HighRiskReviewDO> page(@Param("status") String status, @Param("cursorTs") LocalDateTime cursorTs,
                                @Param("cursorId") String cursorId, @Param("size") int size);
}
