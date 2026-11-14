package com.foundation.notify.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.notify.infrastructure.persistence.MessageReceiverDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 消息收件人 Mapper：常规 CRUD 走 {@link BaseMapper}，跨表分页与保留期清理用注解 SQL。 */
@Mapper
public interface MessageReceiverMapper extends BaseMapper<MessageReceiverDO> {

    /**
     * 「我的消息」游标分页：按 {@code (kind, read)} 过滤并倒序。
     *
     * <p>{@code read} 过滤用 {@code (read_at IS NULL) = #{read}} 表达读 / 未读条件；
     * 游标为上一页末行主键（{@code r.id}）。
     */
    @Select("""
            <script>
            SELECT r.*
            FROM fdn_message_receiver r
            JOIN fdn_message m ON m.id = r.message_id AND m.deleted = false
            WHERE r.deleted = false AND r.person_id = #{personId}
            <if test="kind != null"> AND m.kind = #{kind}</if>
            <if test="read != null"> AND (r.read_at IS NULL) = #{read}</if>
            <if test="cursor != null"> AND r.id &lt; #{cursor}</if>
            ORDER BY r.id DESC
            LIMIT #{size}
            </script>
            """)
    List<MessageReceiverDO> pageByPerson(@Param("personId") String personId,
                                         @Param("kind") String kind,
                                         @Param("read") Boolean read,
                                         @Param("cursor") String cursor,
                                         @Param("size") int size);

    /** 保留期清理：物理删除超过保留期的已读接收行（接收行是事务行，非主数据）。 */
    @Delete("DELETE FROM fdn_message_receiver WHERE read_at IS NOT NULL AND read_at < #{before}")
    int deleteReadBefore(@Param("before") LocalDateTime before);
}
