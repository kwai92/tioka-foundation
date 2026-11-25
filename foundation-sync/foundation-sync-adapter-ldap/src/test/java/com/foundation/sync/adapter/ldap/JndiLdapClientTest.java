package com.foundation.sync.adapter.ldap;

import com.foundation.common.api.error.BizException;
import com.foundation.sync.spi.CredentialResolver;
import com.foundation.sync.spi.SyncCredentialsRef;
import org.junit.jupiter.api.Test;

import javax.naming.Context;
import java.util.Hashtable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LDAP 凭证装配单测（ADR-042，F-08 BAS-12）：覆盖 {@code credential_ref} 解析与明文回落，不启动 Spring 容器。
 *
 * <p>核心是锁定一条**安全不变式**：JNDI 的 {@link Context#SECURITY_CREDENTIALS} 只能是解析后的明文口令，
 * **绝不能是 {@code secret://…} 引用原文**。改造前 {@code environment()} 有一个
 * {@code else if (credentials.secretRef() != null)} 分支会把引用字符串直接当口令塞入，
 * 导致「配了引用反而必然绑定失败、且报错信息看不出原因」。
 */
class JndiLdapClientTest {

    private static final String REF = "secret://foundation/ldap-bind";

    /** 记录型解析器：返回固定明文，并记录被解析的引用。 */
    private static final class StubResolver implements CredentialResolver {

        private final String plain;
        private String resolvedRef;

        StubResolver(String plain) {
            this.plain = plain;
        }

        @Override
        public String resolve(String secretRef) {
            this.resolvedRef = secretRef;
            return plain;
        }
    }

    /** 解析必失败的解析器。 */
    private static final class FailingResolver implements CredentialResolver {

        @Override
        public String resolve(String secretRef) {
            throw new BizException(com.foundation.common.api.error.ErrorCode.NOT_FOUND, "参数不存在");
        }
    }

    private static LdapProperties props(String url, String bindDn, String bindPassword) {
        LdapProperties p = new LdapProperties();
        p.setUrl(url);
        p.setBindDn(bindDn);
        p.setBindPassword(bindPassword);
        return p;
    }

    @Test
    void 有引用且装载解析器时凭证为解析明文而非引用原文() {
        StubResolver resolver = new StubResolver("s3cr3t-from-param-center");
        JndiLdapClient client = new JndiLdapClient(props("ldap://h:389", "cn=admin", ""), resolver);

        client.authenticate(new SyncCredentialsRef("LDAP", REF));
        Hashtable<String, Object> env = client.environment();

        assertThat(env.get(Context.SECURITY_CREDENTIALS)).isEqualTo("s3cr3t-from-param-center");
        // 安全不变式：凭证位绝不出现引用原文
        assertThat(env.get(Context.SECURITY_CREDENTIALS)).isNotEqualTo(REF);
        assertThat(resolver.resolvedRef).isEqualTo(REF);
    }

    @Test
    void 明文配置与引用同时存在时引用优先() {
        StubResolver resolver = new StubResolver("from-ref");
        JndiLdapClient client = new JndiLdapClient(
                props("ldap://h:389", "cn=admin", "legacy-plaintext"), resolver);

        client.authenticate(new SyncCredentialsRef("LDAP", REF));

        assertThat(client.environment().get(Context.SECURITY_CREDENTIALS)).isEqualTo("from-ref");
    }

    @Test
    void 解析失败时authenticate立即抛异常且不回落明文() {
        // 明文口令存在，但引用解析失败：必须失败而非悄悄用明文，否则配置错误被掩盖成认证失败
        JndiLdapClient client = new JndiLdapClient(
                props("ldap://h:389", "cn=admin", "legacy-plaintext"), new FailingResolver());

        assertThatThrownBy(() -> client.authenticate(new SyncCredentialsRef("LDAP", REF)))
                .isInstanceOf(BizException.class);
    }

    @Test
    void 未配引用时回落明文口令() {
        JndiLdapClient client = new JndiLdapClient(
                props("ldap://h:389", "cn=admin", "local-dev-password"), new StubResolver("unused"));

        client.authenticate(new SyncCredentialsRef("LDAP", null));

        assertThat(client.environment().get(Context.SECURITY_CREDENTIALS))
                .isEqualTo("local-dev-password");
    }

    @Test
    void 既无引用又无明文时不设置凭证位() {
        // 回归护栏：改造前此场景会把 secretRef 塞进凭证位；现在宁可匿名（无凭证位）也不用引用原文
        JndiLdapClient client = new JndiLdapClient(
                props("ldap://h:389", "", ""), new StubResolver("unused"));

        client.authenticate(new SyncCredentialsRef("LDAP", null));
        Hashtable<String, Object> env = client.environment();

        assertThat(env).doesNotContainKey(Context.SECURITY_CREDENTIALS);
        assertThat(env.values()).doesNotContain(REF);
    }

    @Test
    void 引用存在但未装载解析器时回落明文而非引用原文() {
        // 插件形态：只装载适配器、未装载 sync-biz（无 CredentialResolver）
        JndiLdapClient client = new JndiLdapClient(props("ldap://h:389", "cn=admin", "fallback-pw"));

        client.authenticate(new SyncCredentialsRef("LDAP", REF));

        assertThat(client.environment().get(Context.SECURITY_CREDENTIALS)).isEqualTo("fallback-pw");
        assertThat(client.environment().get(Context.SECURITY_CREDENTIALS)).isNotEqualTo(REF);
    }

    @Test
    void 未调用authenticate时仍用明文口令不回归() {
        JndiLdapClient client = new JndiLdapClient(
                props("ldap://h:389", "cn=admin", "direct-pw"), new StubResolver("unused"));

        Hashtable<String, Object> env = client.environment();

        assertThat(env.get(Context.SECURITY_CREDENTIALS)).isEqualTo("direct-pw");
        assertThat(env.get(Context.SECURITY_PRINCIPAL)).isEqualTo("cn=admin");
        assertThat(env.get(Context.PROVIDER_URL)).isEqualTo("ldap://h:389");
    }
}
