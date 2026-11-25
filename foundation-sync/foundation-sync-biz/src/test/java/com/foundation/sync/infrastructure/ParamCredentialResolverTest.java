package com.foundation.sync.infrastructure;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.param.ParamApi;
import com.foundation.common.api.param.ParamCmd;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ParamCredentialResolver} 行为（ADR-042 §1.6 / F-08 BAS-11 §9）。
 *
 * <p>重点验证两条安全属性：① 引用格式非法/密钥缺失**立即失败**，
 * ② **绝不把引用原文当明文返回**（这正是改造前 LDAP 客户端的缺陷行为）。
 */
class ParamCredentialResolverTest {

    /** 记录型 stub：可控返回值、记录被调用的 (scope, key)、可模拟异常。 */
    private static final class StubParamApi implements ParamApi {

        private String secretValue;
        private RuntimeException throwOnSecret;
        private final List<String[]> secretCalls = new ArrayList<>();

        StubParamApi returning(String value) {
            this.secretValue = value;
            return this;
        }

        StubParamApi throwing(RuntimeException ex) {
            this.throwOnSecret = ex;
            return this;
        }

        @Override
        public String value(String appCode, String key) {
            throw new UnsupportedOperationException("not used by resolver");
        }

        @Override
        public String secretValue(String appCode, String key) {
            secretCalls.add(new String[]{appCode, key});
            if (throwOnSecret != null) {
                throw throwOnSecret;
            }
            return secretValue;
        }

        @Override
        public void set(ParamCmd cmd) {
            throw new UnsupportedOperationException("not used by resolver");
        }
    }

    @Test
    void 合法引用解析为参数中心明文并按作用域与键调用() {
        StubParamApi params = new StubParamApi().returning("real-app-secret");
        ParamCredentialResolver resolver = new ParamCredentialResolver(params);

        String value = resolver.resolve("secret://foundation/feishu-app-secret");

        assertThat(value).isEqualTo("real-app-secret");
        assertThat(params.secretCalls).hasSize(1);
        assertThat(params.secretCalls.get(0)[0]).isEqualTo("foundation");
        assertThat(params.secretCalls.get(0)[1]).isEqualTo("feishu-app-secret");
    }

    @Test
    void 解析结果绝不等于引用原文() {
        // 安全回归护栏：即便参数中心返回了与引用同形的字符串，也必须是「解密后的值」而非引用本身。
        // 本用例用正常明文断言 resolver 不会走「拿不到值就返回 secretRef」的兜底路径。
        StubParamApi params = new StubParamApi().returning("plain");
        ParamCredentialResolver resolver = new ParamCredentialResolver(params);

        String value = resolver.resolve("secret://foundation/ldap-bind");

        assertThat(value).isNotEqualTo("secret://foundation/ldap-bind");
        assertThat(value).doesNotStartWith("secret://");
    }

    @Test
    void 密钥为空时抛NOT_FOUND而非回落引用原文() {
        StubParamApi params = new StubParamApi().returning("   ");
        ParamCredentialResolver resolver = new ParamCredentialResolver(params);

        assertThatThrownBy(() -> resolver.resolve("secret://foundation/feishu-app-secret"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void 密钥不存在时透传参数中心的异常() {
        StubParamApi params = new StubParamApi()
                .throwing(new BizException(ErrorCode.NOT_FOUND, "参数不存在：x"));
        ParamCredentialResolver resolver = new ParamCredentialResolver(params);

        assertThatThrownBy(() -> resolver.resolve("secret://foundation/missing"))
                .isInstanceOf(BizException.class);
        assertThat(params.secretCalls).hasSize(1);
    }

    @Test
    void 缺少作用域段时抛BAD_REQUEST() {
        ParamCredentialResolver resolver = new ParamCredentialResolver(new StubParamApi());

        // secret:///key → scope 为空
        assertThatThrownBy(() -> resolver.resolve("secret:///only-key"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void 缺少键段时抛BAD_REQUEST() {
        ParamCredentialResolver resolver = new ParamCredentialResolver(new StubParamApi());

        // secret://scope/ → key 为空
        assertThatThrownBy(() -> resolver.resolve("secret://foundation/"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void 非secret协议或空白引用不被支持且解析失败() {
        ParamCredentialResolver resolver = new ParamCredentialResolver(new StubParamApi());

        assertThat(resolver.supports("secret://foundation/x")).isTrue();
        assertThat(resolver.supports("vault://foundation/x")).isFalse();
        assertThat(resolver.supports("  ")).isFalse();
        assertThat(resolver.supports(null)).isFalse();

        assertThatThrownBy(() -> resolver.resolve("foundation/feishu-app-secret"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void 键含多段斜杠时仅首段为作用域() {
        // secret://app/a/b/c → scope=app, key=a/b/c（参数键允许含斜杠，作用域取第一段）
        StubParamApi params = new StubParamApi().returning("v");
        ParamCredentialResolver resolver = new ParamCredentialResolver(params);

        resolver.resolve("secret://app/a/b/c");

        assertThat(params.secretCalls.get(0)[0]).isEqualTo("app");
        assertThat(params.secretCalls.get(0)[1]).isEqualTo("a/b/c");
    }

    @Test
    void 构造器拒绝空参数中心() {
        assertThatThrownBy(() -> new ParamCredentialResolver(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
