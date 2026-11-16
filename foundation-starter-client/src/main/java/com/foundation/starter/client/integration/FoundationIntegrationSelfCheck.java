package com.foundation.starter.client.integration;

import com.foundation.starter.client.authz.AuthorizationSnapshotClient;
import com.foundation.starter.core.config.FoundationProperties;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.foundation.starter.client.integration.IntegrationSelfCheckReport.Status.FAIL;
import static com.foundation.starter.client.integration.IntegrationSelfCheckReport.Status.PASS;
import static com.foundation.starter.client.integration.IntegrationSelfCheckReport.Status.WARN;

/**
 * 派生应用接入自检器（ADR-051 第 4 条，平台资产，不依赖任何业务应用）。
 *
 * <p>覆盖六项可在本地执行的检查：应用编码登记、基座地址、服务账号凭据、授权快照可达、
 * 判定链路 fail-closed、快照失效钩子。前三项是静态登记校验，后三项需要应用传入探针：
 * {@code probePersonId} 为 {@code null} 时快照往返检查记为 WARN，
 * {@code decisionProbe} 为 {@code null} 时 fail-closed 检查记为 WARN。
 *
 * <p><b>刻意不自动执行</b>：自检会真实调用中台端点，只应由应用测试或运维端点显式触发，
 * 不能挂在启动流程里把「中台暂时不可达」变成「应用起不来」。启动期另由
 * {@code FoundationIntegrationAutoConfiguration} 做零网络调用的静态护栏。
 */
public final class FoundationIntegrationSelfCheck {

    /** 应用编码登记规则，与 {@code HttpAuthorizationSnapshotClient} 的路径校验同源。 */
    public static final String APP_CODE_PATTERN = "^[a-z][a-z0-9-]{1,31}$";

    private static final Pattern APP_CODE = Pattern.compile(APP_CODE_PATTERN);

    private final FoundationProperties properties;
    private final ObjectProvider<AuthorizationSnapshotClient> clients;
    private final ObjectProvider<SnapshotInvalidationHook> hooks;

    public FoundationIntegrationSelfCheck(FoundationProperties properties,
                                          ObjectProvider<AuthorizationSnapshotClient> clients,
                                          ObjectProvider<SnapshotInvalidationHook> hooks) {
        if (properties == null || clients == null || hooks == null) {
            throw new IllegalArgumentException("FoundationProperties / clients / hooks are required");
        }
        this.properties = properties;
        this.clients = clients;
        this.hooks = hooks;
    }

    public static boolean isValidAppCode(String appCode) {
        return appCode != null && APP_CODE.matcher(appCode).matches();
    }

    public static boolean isValidBaseUrl(String baseUrl) {
        return baseUrl != null
                && (baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))
                && baseUrl.length() > "https://".length();
    }

    /**
     * 执行一次自检。
     *
     * @param probePersonId 用于真实快照往返的探针人员 ID；{@code null} 表示跳过该检查
     * @param decisionProbe 应用自己的授权判定（输入中台原始权限项，返回是否放行）；
     *                      自检会用 {@code null} 数据源调用它，验证「不可用时不放行」
     */
    public IntegrationSelfCheckReport run(String probePersonId, Predicate<Set<String>> decisionProbe) {
        List<IntegrationSelfCheckReport.Check> checks = new ArrayList<>();
        checks.add(checkAppCode());
        checks.add(checkBaseUrl());
        checks.add(checkServiceAccount());
        checks.add(checkSnapshot(probePersonId));
        checks.add(checkFailClosed(decisionProbe));
        checks.add(checkInvalidation());
        return new IntegrationSelfCheckReport(checks);
    }

    private IntegrationSelfCheckReport.Check checkAppCode() {
        String appCode = properties.getAppCode();
        if (appCode == null || appCode.isBlank()) {
            return check("app-code", FAIL, "foundation.app-code 未登记，中台无法识别接入应用");
        }
        if (!isValidAppCode(appCode)) {
            return check("app-code", FAIL, "应用编码不符合登记规则 " + APP_CODE_PATTERN + "：" + appCode);
        }
        return check("app-code", PASS, "应用编码 " + appCode + " 已按平台规则登记");
    }

    private IntegrationSelfCheckReport.Check checkBaseUrl() {
        String baseUrl = properties.getBaseUrl();
        return isValidBaseUrl(baseUrl)
                ? check("base-url", PASS, "基座地址为 http(s) 绝对地址")
                : check("base-url", FAIL, "foundation.base-url 必须是 http(s) 绝对地址");
    }

    private IntegrationSelfCheckReport.Check checkServiceAccount() {
        FoundationProperties.ServiceAccount sa = properties.getServiceAccount();
        boolean complete = sa != null
                && notBlank(sa.getClientId())
                && notBlank(sa.getClientSecret())
                && notBlank(sa.getTokenUri());
        return complete
                ? check("service-account", PASS, "服务账号凭据三项齐备，出站令牌可用")
                : check("service-account", WARN,
                        "服务账号凭据不完整；若由网关或外部注入令牌可忽略，否则中台端点会拒绝请求");
    }

    private IntegrationSelfCheckReport.Check checkSnapshot(String probePersonId) {
        AuthorizationSnapshotClient client;
        try {
            client = clients.getIfAvailable();
        } catch (RuntimeException ex) {
            return check("snapshot", FAIL, "授权快照客户端装配冲突：" + ex.getClass().getSimpleName());
        }
        if (client == null) {
            return check("snapshot", FAIL, "未装配 AuthorizationSnapshotClient，检查 foundation.client.mode 与依赖");
        }
        if (probePersonId == null || probePersonId.isBlank()) {
            return check("snapshot", WARN, "未提供探针人员 ID，跳过快照往返（静态装配检查已通过）");
        }
        try {
            Set<String> permissions = client.permissionsFor(probePersonId);
            if (permissions == null) {
                return check("snapshot", FAIL, "快照返回 null（不可用）；应用必须按 fail-closed 拒绝，不得当作零权限");
            }
            return check("snapshot", PASS, "快照往返成功，返回 " + permissions.size() + " 项中台原始权限项");
        } catch (RuntimeException ex) {
            return check("snapshot", FAIL, "快照往返异常：" + ex.getClass().getSimpleName());
        }
    }

    private IntegrationSelfCheckReport.Check checkFailClosed(Predicate<Set<String>> decisionProbe) {
        if (decisionProbe == null) {
            return check("fail-closed", WARN, "未提供授权判定探针，跳过 fail-closed 验证");
        }
        try {
            boolean allowed = Boolean.TRUE.equals(decisionProbe.test(null));
            return allowed
                    ? check("fail-closed", FAIL, "数据源为 null 时判定器仍放行，存在越权风险")
                    : check("fail-closed", PASS, "数据源不可用时判定器拒绝放行");
        } catch (RuntimeException ex) {
            return check("fail-closed", PASS,
                    "数据源不可用时判定器以异常拒绝（" + ex.getClass().getSimpleName() + "），同样不放行");
        }
    }

    private IntegrationSelfCheckReport.Check checkInvalidation() {
        try {
            return hooks.getIfAvailable() == null
                    ? check("snapshot-invalidation", WARN,
                            "未注册快照失效钩子；授权变更只能等缓存 TTL 到期后回源")
                    : check("snapshot-invalidation", PASS, "已注册快照失效钩子");
        } catch (RuntimeException ex) {
            return check("snapshot-invalidation", FAIL, "失效钩子装配冲突：" + ex.getClass().getSimpleName());
        }
    }

    private static IntegrationSelfCheckReport.Check check(String name,
                                                          IntegrationSelfCheckReport.Status status,
                                                          String detail) {
        return new IntegrationSelfCheckReport.Check(name, status, detail);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
