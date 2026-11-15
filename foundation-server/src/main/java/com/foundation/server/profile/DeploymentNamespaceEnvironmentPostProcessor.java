package com.foundation.server.profile;

import com.foundation.starter.core.namespace.DeploymentNamespace;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 把 {@code foundation.deployment-id} 展开为一组服务级命名默认值（ADP-07 / P5）。
 *
 * <p>与第二批的装配 profile 同一手法：以 {@code addLast} 注入<b>最低优先级</b>属性源，
 * application.yml 用 {@code ${FOUNDATION_XXX:${foundation.namespace.xxx}}} 引用，于是：
 *
 * <ul>
 *   <li>部署方显式设置 {@code FOUNDATION_KEYCLOAK_REALM} / {@code FOUNDATION_EVENT_TOPIC} 等 → 优先；</li>
 *   <li>未设置但设置了 {@code FOUNDATION_DEPLOYMENT_ID=prod-a} → 落到派生值
 *       {@code enterprise-prod-a} / {@code fdn-events-prod-a} / {@code fdn:prod-a:}；</li>
 *   <li>两者都未设置（{@code default}）→ 派生值逐字等于改造前的字面量，存量部署零改动。</li>
 * </ul>
 *
 * <p>为什么不直接写四份 profile yml：realm 还被 {@code jwk-set-uri} / {@code issuer} 引用，
 * 分散在多处字面量会随部署漂移导致令牌 {@code iss} 校验失败；集中派生可保证三者一致。
 *
 * <p>本处理器只作用于 {@code foundation-server} 自身。接入方业务项目通过 core starter 暴露的
 * {@link DeploymentNamespace} Bean 得到同一套派生规则（见 CoreAutoConfiguration）。
 */
public class DeploymentNamespaceEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    /** 本处理器注入的默认属性源名。 */
    public static final String PROPERTY_SOURCE_NAME = "foundationDeploymentNamespaceDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        String deploymentId = environment.getProperty(DeploymentNamespace.PROPERTY,
                DeploymentNamespace.DEFAULT_ID);
        String keycloakUrl = environment.getProperty("foundation.keycloak.server-url",
                DeploymentNamespace.DEFAULT_KEYCLOAK_URL);

        // 非法 deployment-id 在此处快速失败，避免带错命名空间启动后半途才暴露
        DeploymentNamespace namespace = DeploymentNamespace.of(deploymentId);
        MapPropertySource source = new MapPropertySource(
                PROPERTY_SOURCE_NAME, namespace.defaults(keycloakUrl));

        // 重复调用（上下文刷新、测试重入）时替换而非叠加
        environment.getPropertySources().remove(PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addLast(source);
    }

    @Override
    public int getOrder() {
        // 先于装配 profile EPP 执行，两者互不依赖但便于日志按「命名空间 → profile」顺序阅读
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}
