package com.foundation.starter.data.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * 审计字段自动填充（48-CS §5「逻辑删 / 填充 → MP 全局配置 / MetaObjectHandler」）。
 *
 * <p>操作人来源由调用方注入：BAS-01 之前无请求上下文，默认取 {@code system}；BAS-10 接入真实主体后
 * 由上层提供 {@link Supplier}。
 */
public class AuditMetaObjectHandler implements MetaObjectHandler {

    /** 无请求上下文时的兜底操作人。 */
    public static final String SYSTEM_OPERATOR = "system";

    private final Supplier<String> currentOperator;

    public AuditMetaObjectHandler() {
        this(() -> SYSTEM_OPERATOR);
    }

    public AuditMetaObjectHandler(Supplier<String> currentOperator) {
        this.currentOperator = currentOperator;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateAt", LocalDateTime.class, now);
        String operator = currentOperator.get();
        strictInsertFill(metaObject, "createBy", String.class, operator);
        strictInsertFill(metaObject, "updateBy", String.class, operator);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateAt", LocalDateTime.class, LocalDateTime.now());
        strictUpdateFill(metaObject, "updateBy", String.class, currentOperator.get());
    }
}
