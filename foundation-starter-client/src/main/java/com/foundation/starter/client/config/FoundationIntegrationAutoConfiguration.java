package com.foundation.starter.client.config;

import com.foundation.starter.client.authz.AuthorizationSnapshotClient;
import com.foundation.starter.client.integration.FoundationIntegrationSelfCheck;
import com.foundation.starter.client.integration.SnapshotInvalidationHook;
import com.foundation.starter.core.condition.ConditionalOnFoundationCapability;
import com.foundation.starter.core.condition.ConditionalOnFoundationRuntimeMode;
import com.foundation.starter.core.config.FoundationProperties;
import com.foundation.starter.core.runtime.FoundationRuntimeMode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 派生应用接入护栏（P0 接入平面）：把「应用怎么算接上中台」收归平台自动装配。
 *
 * <p>只在 {@code FOUNDATION} 模式且 {@code client} 能力开启时激活；本地 / 自管模式一个 Bean
 * 都不注册，存量接入方零影响。自检器是<b>按需调用</b>的 Bean，不在启动时发任何网络请求。
 *
 * <p><b>启动期只做静态护栏，且只拒绝「显式配置但非法」</b>：应用编码写了非法值、基座地址
 * 不是 http(s) 时直接拒绝启动，避免第二个应用带着错配置半接线运行；应用编码缺省不拒绝，
 * 因为 Foundation 自身模块与历史接入方可能不设该键，完整登记检查留给显式自检报告。
 */
@AutoConfiguration(after = FoundationClientAutoConfiguration.class)
@ConditionalOnFoundationCapability("client")
@ConditionalOnFoundationRuntimeMode(FoundationRuntimeMode.FOUNDATION)
public class FoundationIntegrationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FoundationIntegrationSelfCheck foundationIntegrationSelfCheck(
            FoundationProperties properties,
            ObjectProvider<AuthorizationSnapshotClient> clients,
            ObjectProvider<SnapshotInvalidationHook> hooks) {
        String appCode = properties.getAppCode();
        if (appCode != null && !appCode.isBlank()
                && !FoundationIntegrationSelfCheck.isValidAppCode(appCode)) {
            throw new IllegalStateException("foundation.app-code 非法（须匹配 "
                    + FoundationIntegrationSelfCheck.APP_CODE_PATTERN + "）：" + appCode);
        }
        if (!FoundationIntegrationSelfCheck.isValidBaseUrl(properties.getBaseUrl())) {
            throw new IllegalStateException("foundation.base-url 必须是 http(s) 绝对地址");
        }
        return new FoundationIntegrationSelfCheck(properties, clients, hooks);
    }
}
