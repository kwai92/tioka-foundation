package com.foundation.sync.adapter.ldap;

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
    public JndiLdapClient ldapApiClient(LdapProperties properties) {
        return new JndiLdapClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public LdapSyncAdapter ldapSyncAdapter(LdapProperties properties, LdapApiClient client) {
        return new LdapSyncAdapter(properties, client);
    }
}
