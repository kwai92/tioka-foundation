package com.foundation.starter.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.client.dict.DictClient;
import com.foundation.starter.client.dict.HttpDictClient;
import com.foundation.starter.client.http.FoundationHttpClient;
import com.foundation.starter.client.local.LocalDictClient;
import com.foundation.starter.client.local.LocalFoundationData;
import com.foundation.starter.client.param.ParamClient;
import com.foundation.starter.core.config.FoundationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADP-08：三种运行模式的装配互斥性——这是「切了 local 却还在连中台」的回归防线。
 *
 * <p>纯 Spring 上下文测试，不需要 Docker / Redis / RocketMQ，也不需要真实 HTTP 服务。
 */
class RuntimeModeAssemblyTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    FoundationClientAutoConfiguration.class,
                    LocalFoundationClientAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(FoundationProperties.class, () -> {
                FoundationProperties properties = new FoundationProperties();
                properties.setAppCode("tioka");
                return properties;
            });

    @Test
    void 缺省模式装配HTTP客户端_与改造前行为一致() {
        runner.run(context -> {
            assertTrue(context.getBean(DictClient.class) instanceof HttpDictClient);
            assertEquals(1, context.getBeansOfType(FoundationHttpClient.class).size());
            assertTrue(context.getBeansOfType(LocalFoundationData.class).isEmpty());
        });
    }

    @Test
    void 本地模式未开启种子时两套客户端都不装配_接入方自给() {
        runner.withPropertyValues("foundation.client.mode=local")
                .run(context -> {
                    assertTrue(context.getBeansOfType(DictClient.class).isEmpty());
                    assertTrue(context.getBeansOfType(FoundationHttpClient.class).isEmpty());
                    assertTrue(context.getBeansOfType(LocalFoundationData.class).isEmpty());
                });
    }

    @Test
    void 本地模式开启种子后只读客户端来自配置_且不建连中台通道() {
        runner.withPropertyValues(seedProperties("local"))
                .run(context -> {
                    assertTrue(context.getBean(DictClient.class) instanceof LocalDictClient);
                    assertTrue(context.getBeansOfType(FoundationHttpClient.class).isEmpty());
                    assertEquals(1, context.getBeansOfType(LocalFoundationData.class).size());
                    assertEquals("M", context.getBean(DictClient.class).items("gender").get(0).itemCode());
                    assertEquals(Optional.of("20"), context.getBean(ParamClient.class).value("pageSize"));
                });
    }

    @Test
    void 自管模式与本地模式使用同一套独立实现() {
        runner.withPropertyValues(seedProperties("self-managed"))
                .run(context -> {
                    assertTrue(context.getBean(DictClient.class) instanceof LocalDictClient);
                    assertTrue(context.getBeansOfType(FoundationHttpClient.class).isEmpty());
                });
    }

    @Test
    void 能力总开关优先于模式_关闭后两套实现都不装配() {
        runner.withPropertyValues(concat(seedProperties("local"), "foundation.client.enabled=false"))
                .run(context -> {
                    assertTrue(context.getBeansOfType(DictClient.class).isEmpty());
                    assertTrue(context.getBeansOfType(FoundationHttpClient.class).isEmpty());
                });
    }

    @Test
    void 接入方自定义实现优先_本地种子不覆盖() {
        runner.withPropertyValues(seedProperties("local"))
                .withBean(DictClient.class, () -> typeCode -> List.of())
                .run(context -> assertFalse(context.getBean(DictClient.class) instanceof LocalDictClient));
    }

    private static String[] seedProperties(String mode) {
        return new String[]{
                "foundation.client.mode=" + mode,
                "foundation.client.local.enabled=true",
                "foundation.client.local.dict.gender[0].item-code=M",
                "foundation.client.local.dict.gender[0].item-name=男",
                "foundation.client.local.dict.gender[0].sort=1",
                "foundation.client.local.param.pageSize=20"
        };
    }

    private static String[] concat(String[] values, String extra) {
        String[] result = new String[values.length + 1];
        System.arraycopy(values, 0, result, 0, values.length);
        result[values.length] = extra;
        return result;
    }
}
