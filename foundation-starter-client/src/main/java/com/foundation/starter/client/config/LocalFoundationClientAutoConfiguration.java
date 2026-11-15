package com.foundation.starter.client.config;

import com.foundation.starter.client.dict.DictClient;
import com.foundation.starter.client.local.LocalDictClient;
import com.foundation.starter.client.local.LocalFoundationData;
import com.foundation.starter.client.local.LocalOrgClient;
import com.foundation.starter.client.local.LocalParamClient;
import com.foundation.starter.client.local.LocalPersonClient;
import com.foundation.starter.client.org.OrgClient;
import com.foundation.starter.client.org.PersonClient;
import com.foundation.starter.client.param.ParamClient;
import com.foundation.starter.core.condition.ConditionalOnFoundationCapability;
import com.foundation.starter.core.condition.ConditionalOnFoundationRuntimeMode;
import com.foundation.starter.core.config.FoundationProperties;
import com.foundation.starter.core.runtime.FoundationRuntimeMode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 本地 / 自管模式的只读客户端装配（ADP-08）。
 *
 * <p>与 {@link FoundationClientAutoConfiguration} <b>互斥</b>：后者只在
 * {@code FOUNDATION} 模式装配 HTTP 通道，本类只在 {@code LOCAL} / {@code SELF_MANAGED}
 * 且显式开启 {@code foundation.client.local.enabled=true} 时装配配置种子实现。
 * 这样不会出现「切了 local 却仍保留中台出站客户端」的半装配状态。
 *
 * <p><b>默认不装配</b>：接入方更常见的做法是自己注册 {@code DictClient} 等 Bean
 * （{@code @ConditionalOnMissingBean} 的既有扩展点），底座不替业务方编造主数据。
 * 只有需要「零代码、纯配置」起一个轻量服务时才打开种子开关。
 *
 * <p>本装配不创建 OkHttp 客户端、不建连、不引 Redis / RocketMQ，可用于无中间件部署。
 */
@AutoConfiguration(after = FoundationClientAutoConfiguration.class)
@ConditionalOnFoundationCapability("client")
@ConditionalOnFoundationRuntimeMode({FoundationRuntimeMode.LOCAL, FoundationRuntimeMode.SELF_MANAGED})
@ConditionalOnProperty(prefix = "foundation.client.local", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(FoundationClientProperties.class)
public class LocalFoundationClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LocalFoundationData localFoundationData(FoundationClientProperties properties,
                                                   ObjectProvider<FoundationProperties> foundationProperties) {
        FoundationProperties foundation = foundationProperties.getIfAvailable();
        String appCode = foundation == null ? null : foundation.getAppCode();
        return LocalFoundationData.from(properties.getClient().getLocal(), appCode);
    }

    @Bean
    @ConditionalOnMissingBean
    public DictClient dictClient(LocalFoundationData data) {
        return new LocalDictClient(data);
    }

    @Bean
    @ConditionalOnMissingBean
    public ParamClient paramClient(LocalFoundationData data) {
        return new LocalParamClient(data);
    }

    @Bean
    @ConditionalOnMissingBean
    public OrgClient orgClient(LocalFoundationData data) {
        return new LocalOrgClient(data);
    }

    @Bean
    @ConditionalOnMissingBean
    public PersonClient personClient(LocalFoundationData data) {
        return new LocalPersonClient(data);
    }
}
