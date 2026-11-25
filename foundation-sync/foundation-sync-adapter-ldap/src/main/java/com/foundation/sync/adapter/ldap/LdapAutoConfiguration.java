package com.foundation.sync.adapter.ldap;

import com.foundation.sync.spi.CredentialResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * LDAP / AD 同步适配器自动装配（F-08 BAS-12）。
 *
 * <p>关键：{@code @Bean} 返回类型必须用具体类型（{@link JndiLdapClient} / {@link LdapSyncAdapter}），
 * 不得退回接口类型，否则多个来源适配器之间会被 {@code @ConditionalOnMissingBean} 互相顶掉。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "foundation.server.domains", name = "ldap", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LdapProperties.class)
public class LdapAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JndiLdapClient ldapApiClient(LdapProperties properties,
                                        ObjectProvider<CredentialResolver> credentialResolver) {
        // ADR-042：解析器由 foundation-sync-biz 提供（复用 ParamApi 解密）。以 ObjectProvider 注入，
        // 使「仅装载本适配器插件、未装载 sync-biz」的场景仍能启动（回落明文并告警），
        // 且不违反 ADR-078 第 8 条（适配器只依赖 SPI 与 -api 契约，不依赖 -biz）。
        return new JndiLdapClient(properties, credentialResolver.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public LdapSyncAdapter ldapSyncAdapter(LdapProperties properties, LdapApiClient client) {
        return new LdapSyncAdapter(properties, client);
    }
}
