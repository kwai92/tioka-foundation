package com.foundation.sync.adapter.feishu;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.sync.spi.CredentialResolver;
import com.foundation.sync.spi.SyncCredentialsRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 飞书凭证装配单测（ADR-042，F-08 BAS-12）：覆盖 {@code credential_ref} 解析与明文回落，不发网络请求。
 *
 * <p>与 {@code JndiLdapClientTest} 对称，锁定同一条**安全不变式**：送往飞书换取
 * {@code tenant_access_token} 的 {@code app_secret} 只能是解析后的明文，
 * **绝不能是 {@code secret://…} 引用原文**——否则对端只会回 401，定位成本极高。
 */
class HttpFeishuApiClientTest {

    private static final String REF = "secret://foundation/feishu-app-secret";

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

    private static final class FailingResolver implements CredentialResolver {

        @Override
        public String resolve(String secretRef) {
            throw new BizException(ErrorCode.NOT_FOUND, "参数不存在");
        }
    }

    private static FeishuProperties props(String appSecret) {
        FeishuProperties p = new FeishuProperties();
        p.setBaseUrl("https://open.feishu.cn");
        p.setAppId("cli_test");
        p.setAppSecret(appSecret);
        return p;
    }

    @Test
    void 有引用且装载解析器时密钥为解析明文而非引用原文() {
        StubResolver resolver = new StubResolver("s3cr3t-from-param-center");
        HttpFeishuApiClient client = new HttpFeishuApiClient(props(""), resolver);

        client.authenticate(new SyncCredentialsRef("FEISHU", REF));

        assertThat(client.appSecret()).isEqualTo("s3cr3t-from-param-center");
        // 安全不变式：绝不用引用原文当密钥
        assertThat(client.appSecret()).isNotEqualTo(REF);
        assertThat(client.appSecret()).doesNotStartWith("secret://");
        assertThat(resolver.resolvedRef).isEqualTo(REF);
    }

    @Test
    void 明文配置与引用同时存在时引用优先() {
        StubResolver resolver = new StubResolver("from-ref");
        HttpFeishuApiClient client = new HttpFeishuApiClient(props("legacy-plaintext"), resolver);

        client.authenticate(new SyncCredentialsRef("FEISHU", REF));

        assertThat(client.appSecret()).isEqualTo("from-ref");
    }

    @Test
    void 解析失败时authenticate立即抛异常且不回落明文() {
        // 明文密钥存在，但引用解析失败：必须失败而非悄悄用明文，否则配置错误被掩盖成 401
        HttpFeishuApiClient client = new HttpFeishuApiClient(props("legacy-plaintext"), new FailingResolver());

        assertThatThrownBy(() -> client.authenticate(new SyncCredentialsRef("FEISHU", REF)))
                .isInstanceOf(BizException.class);
    }

    @Test
    void 未配引用时回落明文密钥() {
        HttpFeishuApiClient client = new HttpFeishuApiClient(props("local-dev-secret"), new StubResolver("unused"));

        client.authenticate(new SyncCredentialsRef("FEISHU", null));

        assertThat(client.appSecret()).isEqualTo("local-dev-secret");
    }

    @Test
    void 引用存在但未装载解析器时回落明文而非引用原文() {
        // 插件形态：只装载适配器、未装载 sync-biz（无 CredentialResolver）
        HttpFeishuApiClient client = new HttpFeishuApiClient(props("fallback-secret"));

        client.authenticate(new SyncCredentialsRef("FEISHU", REF));

        assertThat(client.appSecret()).isEqualTo("fallback-secret");
        assertThat(client.appSecret()).isNotEqualTo(REF);
    }

    @Test
    void 未调用authenticate时仍用明文密钥不回归() {
        HttpFeishuApiClient client = new HttpFeishuApiClient(props("direct-secret"), new StubResolver("unused"));

        assertThat(client.appSecret()).isEqualTo("direct-secret");
    }
}
