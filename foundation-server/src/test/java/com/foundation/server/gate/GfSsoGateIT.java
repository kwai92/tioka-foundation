package com.foundation.server.gate;

import com.foundation.app.api.AppApi;
import com.foundation.app.api.AppClientApi;
import com.foundation.app.api.AppRegistrationCmd;
import com.foundation.app.api.ClientView;
import com.foundation.common.api.keycloak.KeycloakOidcClientAdmin;
import com.foundation.common.id.Ids;
import com.foundation.keycloak.KeycloakProperties;
import com.foundation.org.domain.OrgIdPrefix;
import com.foundation.test.FoundationContainers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 闸门 GF（F-00 §6.1 W82，BAS-04）：新应用登记后自动开通 OIDC 客户端，密钥轮换后旧密钥失效。
 *
 * <p>验收口径（F-00 §6.1 / F-05 §202）：新应用登记后**自动开通 OIDC 客户端并完成单点登录**；
 * **密钥轮换后旧密钥失效**。对应 F-05 §197-200 点名却**尚未存在**的 4 个测试
 * （{@code AppRegistrationIT#clientAutoProvisioned} 等），本闸门覆盖其中不依赖浏览器交互的部分。
 *
 * <p><b>本闸门验什么，而既有单测验不到</b>：{@code AppRegistrar} 的登记链路、
 * {@code ClientSecretRotator} 的轮换、{@code KeycloakOidcClientAdminImpl} 的 upsert/regenerateSecret
 * 都可用内存 stub 验**算法**；本闸门跑完整 {@code FoundationApplication} + PostgreSQL + **真实 Keycloak**，
 * 验三层 stub 碰不到的东西：
 * <ul>
 *   <li>登记是否真的在**真实 Keycloak** 建出 OIDC 客户端（而非只写本地 {@code fdn_app_client}）——
 *       用 {@code rotateSecret} 成功来**反证**：{@code regenerateSecret} 内部 {@code requireUuid} 对
 *       Keycloak 中不存在的 clientId 抛 {@code IllegalStateException}，故轮换成功即证明客户端真被建出。
 *       平台没有「查询 Keycloak client」的接口（{@code KeycloakAdminClient} 只有 realm/ping/accessToken，
 *       {@code KeycloakRealmAdmin} 只有 apply* 系列），故这是间接但可靠的开通信号；</li>
 *   <li><b>旧密钥失效（核心）</b>：这是「密钥明文只在 {@code regenerateSecret} 返回值出现、新旧并存窗口
 *       默认 0 秒」这一安全语义的**端到端兑现**——轮换后拿旧 secret 打真实 token endpoint 必须被拒。
 *       内存 stub 无法证明 Keycloak 真的作废了旧 secret，只有真连 token endpoint 才算数；</li>
 *   <li>{@code fdn_app_client} 的 {@code status} 流转（登记 {@code ROTATING} → 成功 {@code ACTIVE}）
 *       与 {@code redirect_uris}/{@code grant_types} 在真表上的落库形态。</li>
 * </ul>
 *
 * <p><b>如实标注不在本闸门范围的验收点（不夸大覆盖）</b>：
 * <ul>
 *   <li><b>「完成单点登录」的端到端做不了</b>：{@code authorization_code} 全流程需浏览器重定向 +
 *       用户在 Keycloak 登录页认证；测试 realm 的 {@code foundation-admin} 是
 *       {@code directAccessGrantsEnabled=false}（无 password grant），也无法用 ROPC 替代。故本闸门只验到
 *       **SSO 的前提已具备**：OIDC 客户端在真实 Keycloak 中存在、可轮换密钥、{@code redirectUris} 与登记值一致、
 *       {@code grantTypes} 含 {@code authorization_code}（授权码流已启用）。不声称验证了完整登录跳转。</li>
 *   <li><b>不测服务账号 {@code client_credentials}</b>：{@code ServiceAccountService.create} 不写
 *       {@code fdn_app_client}（只写 {@code fdn_service_account} + Keycloak），而 {@code rotateSecret}
 *       依赖 {@code fdn_app_client}；且 {@code AppTokenClient} 是未实现的占位接口（javadoc:「实现归 BAS-30/F-12」）。
 *       F-05 §198 的 {@code serviceAccountClientCredentials} 属另一条链路，不在本闸门范围。</li>
 * </ul>
 *
 * <p><b>「旧密钥失效」为何能用应用登记客户端验（而非必须服务账号）</b>：应用登记客户端是
 * {@code publicClient=false}（机密客户端）+ {@code standardFlowEnabled=true}，它在 token endpoint 用
 * {@code client_id}+{@code client_secret} 做<b>客户端认证</b>来「换码」。轮换后旧 secret 认证失败 →
 * {@code invalid_client} / 401，这正是要验的语义，<b>不需要 client_credentials</b>。
 * （F-05 §199 原文即「旧密钥<b>换码</b>/取令牌 401」——注意「换码」二字，指 authorization_code。）
 *
 * <p><b>清理口径（gate job 共享 static PG + Keycloak 单例）</b>：本闸门在真实 Keycloak 建的 OIDC 客户端
 * 必须在 {@link #cleanUpKeycloak()} 里 {@code delete}，否则残留给同批次其它闸门的 realm。
 * clientId 取应用编码（{@code AppRegistrar} 的规则），故用**完整 ULID 随机位**派生 appCode 保证唯一
 * （见 {@link #newAppCode()}，遵循「不取单调 ULID 时间戳前缀」的教训）。本地表按前缀 DELETE：
 * {@code fdn_app_client} 有 {@code REFERENCES fdn_app(id)}，故先删 client 再删 app；
 * {@code fdn_app} 有 V3/V4 种子，只按本闸门 code 精确删，绝不 TRUNCATE。
 *
 * <p>运行：{@code mvn -B verify -Pgate}（gate profile 由 surefire 以 {@code groups=gate} 执行 IT）。
 */
@Tag("gate")
@SpringBootTest
class GfSsoGateIT {

    /** 本闸门自建应用编码前缀，清理时据此精确 DELETE（不碰 V3/V4 种子应用）。 */
    private static final String APP_PREFIX = "gfsso";
    /** 登记用的回调地址（{@code authorization_code} 流程必填，须为绝对 http/https 地址）。 */
    private static final String REDIRECT_URI = "https://gfsso.example.com/callback";
    private static final String OPERATOR = "gate";

    /** 共享 {@link HttpClient}（token endpoint 调用）。 */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        FoundationContainers.register(registry);
        FoundationContainers.registerKeycloak(registry);
    }

    @Autowired
    private AppApi apps;

    @Autowired
    private AppClientApi clients;

    /** 直接操作真实 Keycloak，仅用于清理自建客户端（平台无「查 client」接口，故开通验证走 rotateSecret 反证）。 */
    @Autowired
    private KeycloakOidcClientAdmin keycloak;

    /** token endpoint 的 serverUrl 与 realm 由平台配置给出，避免测试硬编码。 */
    @Autowired
    private KeycloakProperties keycloakProperties;

    @Autowired
    private JdbcTemplate jdbc;

    /** 本闸门在 Keycloak 建过的 clientId，用于 {@link #cleanUpKeycloak()} 逐个删除。 */
    private final List<String> createdClientIds = new ArrayList<>();

    @BeforeEach
    void cleanUp() {
        cleanUpKeycloak();
        // fdn_app_client 有 REFERENCES fdn_app(id)，先删 client 再删 app
        jdbc.update("DELETE FROM fdn_app_client WHERE client_id LIKE ?", APP_PREFIX + "%");
        // fdn_app 有 V3/V4 种子，只按本闸门前缀精确删，绝不 TRUNCATE
        jdbc.update("DELETE FROM fdn_app WHERE code LIKE ?", APP_PREFIX + "%");
    }

    @AfterEach
    void cleanUpKeycloak() {
        // 共享 Keycloak realm：本闸门建的 OIDC 客户端必须删掉，否则残留给同批次其它闸门。
        // clientId 取应用编码（AppRegistrar 规则），带 gfsso 前缀 + 完整 ULID，跨闸门与跨运行都不会撞。
        for (String clientId : createdClientIds) {
            keycloak.delete(clientId);
        }
        createdClientIds.clear();
    }

    // ---------------------------------------------------------------- ① 自动开通 OIDC 客户端

    /**
     * ① 新应用登记后**自动开通 OIDC 客户端**（F-00 §6.1 / F-05 §197 {@code clientAutoProvisioned}）。
     *
     * <p>走**生产路径** {@link AppApi#register}（内部经 {@code AppRegistrar}：同一事务内
     * {@code apps.insert → clients.insert(ROTATING) → keycloak.upsert → updateStatus(ACTIVE)}），
     * 而非直接调 Keycloak——这样验的是「登记即开通」这条业务链路真的接通。
     *
     * <p>断言分两层：<b>本地</b>（{@code fdn_app_client} 有行、{@code status=ACTIVE}、
     * {@code redirectUris} 与登记值一致、{@code grantTypes} 含 {@code authorization_code}）；
     * <b>真实 Keycloak</b>（用 {@code rotateSecret} 成功反证客户端真被建出，见类 javadoc）。
     */
    @Test
    void registeredAppAutoProvisionsOidcClientInKeycloak() {
        String appCode = newAppCode();
        createdClientIds.add(appCode); // clientId == appCode（AppRegistrar 规则），登记前先记以便清理

        String appId = apps.register(new AppRegistrationCmd(appCode, "闸门SSO应用", null,
                "https://gfsso.example.com", OPERATOR, "SUB", List.of(REDIRECT_URI), OPERATOR));
        assertThat(appId).as("登记应返回应用 ID").isNotBlank();

        // 本地：客户端行已落库且为 ACTIVE，回调与授权类型与登记一致
        List<ClientView> clientList = clients.listByApp(appCode);
        assertThat(clientList)
                .as("登记后应自动开通一个客户端（clientId == appCode）")
                .hasSize(1);
        ClientView client = clientList.get(0);
        assertThat(client.clientId()).isEqualTo(appCode);
        assertThat(client.status())
                .as("Keycloak 开通成功后状态应从 ROTATING 落到 ACTIVE")
                .isEqualTo("ACTIVE");
        assertThat(client.redirectUris())
                .as("回调地址应与登记值一致（SSO 前提）")
                .containsExactly(REDIRECT_URI);
        assertThat(client.grantTypes())
                .as("默认授权类型应含 authorization_code（授权码流已启用，是 SSO 的前提）")
                .contains("authorization_code");

        // 真实 Keycloak：rotateSecret 成功即反证客户端真被建出（requireUuid 对不存在的 clientId 抛异常）
        String secret = clients.rotateSecret(appCode, appCode, OPERATOR);
        assertThat(secret)
                .as("能在真实 Keycloak 上轮换密钥，反证该 OIDC 客户端已真实开通（而非只写了本地表）")
                .isNotBlank();
    }

    // ---------------------------------------------------------------- ② 密钥轮换后旧密钥失效

    /**
     * ② 密钥轮换后**旧密钥立即失效**（F-00 §6.1 / F-05 §199 {@code oldSecretRejected}）——本闸门核心。
     *
     * <p>在<b>真实 Keycloak token endpoint</b> 上验证，靠<b>认证结果差异</b>区分新旧 secret，避免弱断言假绿：
     * <ol>
     *   <li>第一次轮换拿到 secret A；第二次轮换拿到 secret B（{@code ClientSecretRotator} javadoc:
     *       「立即作废旧密钥，新旧并存窗口默认 0 秒」）；</li>
     *   <li>用 <b>A</b> 打 token endpoint（{@code authorization_code} grant，带 client_id/client_secret/code/redirect_uri）
     *       → 客户端认证失败 → <b>HTTP 401</b>；</li>
     *   <li>用 <b>B</b> 打同样请求 → 客户端认证<b>通过</b>、仅 code 是假的 → <b>非 401</b>。</li>
     * </ol>
     * <b>刻意不硬绑具体 error 名</b>：CI 实测 Keycloak 对失效 client_secret 返回 {@code unauthorized_client}
     * 而非 OAuth2 规范常见的 {@code invalid_client}，且该命名随 Keycloak 版本可能变化。本闸门的语义是
     * 「旧密钥被拒、新密钥不被拒」，故只断言 HTTP 401 vs 非 401、并额外断言<b>两者错误码相异</b>
     * （坐实差异来自 secret 新旧而非请求本身一直就错）。两个响应先收集后断言，避免在 A 处提前抛出而看不到 B。
     *
     * <p>用 {@code authorization_code} 而非 {@code client_credentials}：应用登记客户端只开了授权码流
     * （{@code standardFlowEnabled=true}、{@code serviceAccountsEnabled=false}），换码正是它的真实用途，
     * 也对应 F-05 §199「旧密钥<b>换码</b>」的原文。
     */
    @Test
    void rotatedSecretImmediatelyInvalidatesOldSecret() {
        String appCode = newAppCode();
        createdClientIds.add(appCode);
        apps.register(new AppRegistrationCmd(appCode, "闸门SSO轮换应用", null,
                "https://gfsso.example.com", OPERATOR, "SUB", List.of(REDIRECT_URI), OPERATOR));

        // 两次轮换：A 是被作废的旧密钥，B 是当前有效密钥
        String oldSecret = clients.rotateSecret(appCode, appCode, OPERATOR);
        String newSecret = clients.rotateSecret(appCode, appCode, OPERATOR);
        assertThat(newSecret)
                .as("两次轮换应产生不同的密钥（否则说明 regenerateSecret 没真换）")
                .isNotEqualTo(oldSecret);

        // 收集两个响应后再断言：避免在旧密钥处提前抛出而看不到新密钥的实际响应（判据②：
        // 诊断要挂在失败真能走到的地方，取证字段须在目标失败形态下真的有值）。
        TokenResponse oldResp = exchangeCode(appCode, oldSecret);
        TokenResponse newResp = exchangeCode(appCode, newSecret);

        // 旧密钥：客户端认证失败 → HTTP 401。
        // 刻意不硬绑具体 error 名：CI 实测 Keycloak 对失效 client_secret 返回 unauthorized_client
        // （而非 OAuth2 规范常见的 invalid_client），且该命名随 Keycloak 版本可能不同。本闸门的语义是
        // 「旧密钥被拒、新密钥不被拒」，故只断言认证结果（401 vs 非 401）与两者错误码相异，不绑字面量。
        assertThat(oldResp.statusCode())
                .as("旧密钥换码必须被拒（HTTP 401 客户端认证失败）；实际响应=%s", oldResp)
                .isEqualTo(401);

        // 新密钥：客户端认证通过（非 401），仅因 code 是假的而失败。
        assertThat(newResp.statusCode())
                .as("新密钥必须通过客户端认证（非 401），否则说明轮换把有效密钥也作废了；实际响应=%s", newResp)
                .isNotEqualTo(401);

        // 关键区分：旧、新密钥错误码不同 → 证明差异来自 secret 新旧，而非请求本身一直就错
        // （若两者相同，「旧密钥失效」无从与「请求本来就是错的」区分，属弱断言假绿）。
        assertThat(newResp.error())
                .as("新密钥的错误码必须不同于旧密钥（旧=%s 新=%s）", oldResp.error(), newResp.error())
                .isNotEqualTo(oldResp.error());
    }

    // ---------------------------------------------------------------- 覆盖自证

    /**
     * 验收口径覆盖自证：F-00 §6.1 W82 / F-05 §202 点名的两段验收必须**各有对应的 @Test 方法**。
     *
     * <p>方法名在此**刻意硬编码**：删改任一验收用例即红，防「闸门已建」与「验收点已覆盖」被混为一谈
     * （沿用 {@code GfPermissionGateIT} / {@code GfAccountLifecycleGateIT} 同款惯例）。
     * 「完成单点登录」的端到端不在清单内（按分层与 realm 配置做不了，见类 javadoc 的如实标注）。
     */
    @Test
    void allServerSideAcceptancePointsAreCovered() {
        Set<String> acceptancePoints = Set.of(
                "registeredAppAutoProvisionsOidcClientInKeycloak",   // ① 自动开通 OIDC 客户端
                "rotatedSecretImmediatelyInvalidatesOldSecret");      // ② 密钥轮换后旧密钥失效

        Set<String> declaredTests = Arrays.stream(getClass().getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Test.class))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(declaredTests)
                .as("F-00 §6.1 W82 的服务端验收点必须各有对应 @Test，缺一条即视为闸门不完整")
                .containsAll(acceptancePoints);
    }

    // ---------------------------------------------------------------- token endpoint 与造数

    /**
     * 用 {@code authorization_code} grant 打真实 Keycloak token endpoint，以 client_id/client_secret 做客户端认证。
     *
     * <p>code 用一个必然无效的随机串——本方法的目的不是完成登录，而是让 Keycloak 走到
     * 「先验客户端认证、再验 code」这一步：secret 错 → 卡在认证阶段返 {@code invalid_client}/401；
     * secret 对 → 过了认证、卡在 code 阶段返 {@code invalid_grant}/400。两者错误码不同即可区分密钥新旧。
     */
    private TokenResponse exchangeCode(String clientId, String clientSecret) {
        String form = "grant_type=authorization_code"
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&code=" + enc("gate-invalid-code-" + Ids.newId(OrgIdPrefix.PERSON).substring(4))
                + "&redirect_uri=" + enc(REDIRECT_URI);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint()))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            return new TokenResponse(response.statusCode(), response.body());
        } catch (Exception ex) {
            throw new AssertionError("调用 Keycloak token endpoint 失败：" + ex.getMessage(), ex);
        }
    }

    /** token endpoint 由平台配置的 serverUrl + realm 拼出，不硬编码。 */
    private String tokenEndpoint() {
        String base = keycloakProperties.getServerUrl().replaceAll("/$", "");
        return base + "/realms/" + keycloakProperties.getRealm() + "/protocol/openid-connect/token";
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 生成唯一应用编码（也是 Keycloak clientId）。
     *
     * <p><b>必须用完整 ULID 随机位，不能取前缀子串</b>：{@code Ids.newId} 返回 {@code "per_" + 单调ULID}，
     * 单调 ULID 前 10 字符是毫秒时间戳，紧密循环内多次调用前缀相同 → appCode 撞车 →
     * {@code fdn_app} 唯一约束或 Keycloak clientId 冲突。{@code substring(4)} 取完整 26 字符 ULID
     * （含随机位），转小写后仍满足 {@code AppCode} 的 {@code ^[a-z][a-z0-9-]{1,31}$}
     * （总长 5+26=31 ≤ 32）。此教训见 MEMORY.md「命令与环境事实」§5 同源变体。
     */
    private String newAppCode() {
        return APP_PREFIX + Ids.newId(OrgIdPrefix.PERSON).substring(4).toLowerCase();
    }

    /** token endpoint 响应（状态码 + 解析出的 {@code error} 字段）。 */
    private record TokenResponse(int statusCode, String body) {
        /** 从 JSON body 里取 {@code error} 字段值；无则返回 {@code null}。用最小解析避免引入 JSON 依赖。 */
        String error() {
            int idx = body == null ? -1 : body.indexOf("\"error\"");
            if (idx < 0) {
                return null;
            }
            int colon = body.indexOf(':', idx);
            int firstQuote = body.indexOf('"', colon + 1);
            int secondQuote = body.indexOf('"', firstQuote + 1);
            if (colon < 0 || firstQuote < 0 || secondQuote < 0) {
                return null;
            }
            return body.substring(firstQuote + 1, secondQuote);
        }
    }
}
