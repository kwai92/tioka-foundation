package com.foundation.starter.core.config;

import com.foundation.starter.core.condition.ConditionalOnFoundationCapability;
import com.foundation.starter.core.namespace.DeploymentNamespace;
import com.foundation.starter.core.runtime.FoundationRuntimeMode;
import com.foundation.starter.core.runtime.FoundationRuntimeModeGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

/**
 * 核心自动装配（F-02 BAS-24 §foundation-starter-core）。
 *
 * <p>本构件只做约定与配置绑定（时间 UTC、错误码、ID、请求上下文均为静态工具）；
 * Jackson 与全局异常等 web 级约定归 {@code foundation-starter-web}。
 *
 * <p>ADP-07 起额外暴露 {@link DeploymentNamespace}：接入方只需设置
 * {@code foundation.deployment-id}，缓存键前缀等派生命名即自动带上部署隔离段，
 * 无需逐个改 {@code foundation.cache.key-prefix} 等配置。
 *
 * <p>ADP-08 起额外暴露 {@link FoundationRuntimeMode}：默认 {@code FOUNDATION} 保持零改动；
 * 切到 {@code LOCAL} / {@code SELF_MANAGED} 时由 {@link FoundationRuntimeModeGuard}
 * 在启动阶段打印模式与接入方仍需补齐的契约提示。
 */
@AutoConfiguration
@ConditionalOnFoundationCapability("core")
@EnableConfigurationProperties({CoreProperties.class, FoundationProperties.class})
public class CoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CoreAutoConfiguration.class);

    /** 接入运行模式；业务项目可用同名 Bean 覆盖，条件注解仍以 Environment 中的配置为准。 */
    @Bean
    @ConditionalOnMissingBean
    public FoundationRuntimeMode foundationRuntimeMode(Environment environment) {
        FoundationRuntimeMode mode = FoundationRuntimeMode.from(environment);
        new FoundationRuntimeModeGuard(mode, environment).validate();
        return mode;
    }

    /** 部署命名空间；业务项目可用同名 Bean 覆盖派生规则。 */
    @Bean
    @ConditionalOnMissingBean
    public DeploymentNamespace foundationDeploymentNamespace(FoundationProperties properties,
                                                             Environment environment) {
        DeploymentNamespace namespace = DeploymentNamespace.of(properties.getDeploymentId());
        if (namespace.isDefault() && !isTestProfile(environment)
                && !ClassUtils.isPresent("org.junit.jupiter.api.Test", getClass().getClassLoader())) {
            log.warn("foundation.deployment-id 使用缺省值 'default'：多套部署共用同一 realm / 事件主题 / "
                    + "缓存键前缀 / 桶前缀会互相干扰，生产环境请显式注入 FOUNDATION_DEPLOYMENT_ID（ADP-07）");
        }
        return namespace;
    }

    /** 单元测试与本地开发不刷告警；生产与集成环境必须提示（JUnit 类路径判定见调用处）。 */
    private static boolean isTestProfile(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("test".equalsIgnoreCase(profile) || "local".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
