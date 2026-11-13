package com.foundation.starter.data.mybatis;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.foundation.starter.data.BaseDO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link AuditMetaObjectHandler} 与 {@link BaseDO} 的填充/逻辑删约定。 */
class AuditMetaObjectHandlerTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SampleDO.class);
    }

    @Test
    void fillsAuditFieldsOnInsert() {
        SampleDO entity = new SampleDO();

        new AuditMetaObjectHandler(() -> "ci@contract").insertFill(metaObject(entity));

        assertThat(entity.getCreateAt()).isNotNull();
        assertThat(entity.getUpdateAt()).isNotNull();
        assertThat(entity.getCreateBy()).isEqualTo("ci@contract");
        assertThat(entity.getUpdateBy()).isEqualTo("ci@contract");
    }

    @Test
    void defaultsToSystemOperatorWithoutRequestContext() {
        SampleDO entity = new SampleDO();

        new AuditMetaObjectHandler().insertFill(metaObject(entity));

        assertThat(entity.getCreateBy()).isEqualTo(AuditMetaObjectHandler.SYSTEM_OPERATOR);
    }

    @Test
    void doesNotOverwriteExplicitValues() {
        SampleDO entity = new SampleDO();
        entity.setCreateBy("manual");
        entity.setCreateAt(LocalDateTime.of(2026, 1, 1, 0, 0));

        new AuditMetaObjectHandler(() -> "ci@contract").insertFill(metaObject(entity));

        assertThat(entity.getCreateBy()).isEqualTo("manual");
        assertThat(entity.getCreateAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(entity.getUpdateBy()).isEqualTo("ci@contract");
    }

    @Test
    void fillsUpdateFieldsOnUpdate() {
        SampleDO entity = new SampleDO();

        new AuditMetaObjectHandler(() -> "admin").updateFill(metaObject(entity));

        assertThat(entity.getUpdateAt()).isNotNull();
        assertThat(entity.getUpdateBy()).isEqualTo("admin");
        assertThat(entity.getCreateBy()).isNull();
    }

    @Test
    void baseDoCarriesLogicalDeleteColumn() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(SampleDO.class);

        assertThat(tableInfo.isWithLogicDelete()).isTrue();
    }

    private static MetaObject metaObject(SampleDO entity) {
        return SystemMetaObject.forObject(entity);
    }

    /** 测试用实体：主键由具体 DO 自持，此处只需继承审计基类。 */
    static class SampleDO extends BaseDO {
    }
}
