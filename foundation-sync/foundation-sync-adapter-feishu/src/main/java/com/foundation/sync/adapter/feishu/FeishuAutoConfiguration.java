package com.foundation.sync.adapter.feishu;

import com.foundation.sync.spi.CredentialResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 飞书同步适配器自动装配（F-08 BAS-12）。
 *
 * <p>以插件方式提供 {@link SyncSourceAdapter} 与 {@link FieldConverter}；由 BAS-11 引擎的
 * {@code AdapterRegistry} 汇总注册，适配器本身不自建 REST。
 *
 * <p><b>注意</b>：{@code @Bean} 方法返回具体类型（{@link HttpFeishuApiClient} /
 * {@link FeishuSyncAdapter} / {@link DepartmentCodePrefixConverter}）而非接口，避免多个适配器
 * 之间因 {@code @ConditionalOnMissingBean} 按接口类型互相顶掉。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "foundation.server.domains", name = "feishu", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FeishuProperties.class)
public class FeishuAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HttpFeishuApiClient feishuApiClient(FeishuProperties properties,
                                              ObjectProvider<CredentialResolver> credentialResolver) {        // ADR-042：解析器由 foundation-sync-biz 提供（复用 ParamApi 解密）。以 ObjectProvider 注入，
        // 使「仅装载本适配器插件、未装载 sync-biz」的场景仍能启动（回落明文并告警），
        // 且不违反 ADR-078 第 8 条（适配器只依赖 SPI 与 -api 契约，不依赖 -biz）。
        return new HttpFeishuApiClient(properties, credentialResolver.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuSyncAdapter feishuSyncAdapter(FeishuProperties properties, FeishuApiClient client) {
        return new FeishuSyncAdapter(properties, client);
    }

    @Bean
    @ConditionalOnMissingBean
    public DepartmentCodePrefixConverter departmentCodePrefixConverter(FeishuProperties properties) {
        return new DepartmentCodePrefixConverter(properties.getDepartmentCodePrefix());
    }
}
