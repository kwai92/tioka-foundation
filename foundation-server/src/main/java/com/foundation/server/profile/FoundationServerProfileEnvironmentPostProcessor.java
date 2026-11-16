package com.foundation.server.profile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把 {@code foundation.server.profile} 展开为各业务域的默认开关（ADP-05）。
 *
 * <p>为什么不直接在 {@code application.yml} 写四份 profile 文件：装配条件分布在 15 个
 * {@code -biz} 模块的 {@code AutoConfiguration} 上，属性必须在**条件求值之前**进入
 * {@code Environment}，且要允许部署方用更高优先级的配置逐域覆盖。这里用
 * {@link MapPropertySource} 以 {@code addLast}（最低优先级）注入默认值：
 *
 * <ul>
 *   <li>未配置 profile → {@code full}，与改造前「全量装配」完全一致；</li>
 *   <li>{@code foundation.server.profile=minimal} → 只把字典/参数/编号所在域置 true；</li>
 *   <li>显式写 {@code foundation.server.domains.file=true} 可覆盖 profile 的默认值；</li>
 *   <li>业务项目不使用本服务端时，各域条件 {@code matchIfMissing=true}，不受影响。</li>
 * </ul>
 *
 * <p>profile 只裁剪服务端 Bean/路由/定时任务；Flyway 迁移仍全量执行（同一套库结构被多种
 * profile 共用，拆分迁移位置会让号段分裂），中间件裁剪由显式的
 * {@code foundation.<capability>.enabled} 控制，见 docs/adaptation/server-profiles.md。
 */
public class FoundationServerProfileEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    /** profile 配置键。 */
    public static final String PROFILE_PROPERTY = "foundation.server.profile";

    /** 域开关前缀：{@code foundation.server.domains.<domain>}。 */
    public static final String DOMAIN_PREFIX = "foundation.server.domains.";

    /** 本处理器注入的默认属性源名。 */
    public static final String PROPERTY_SOURCE_NAME = "foundationServerProfileDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        FoundationServerProfile profile = FoundationServerProfile.from(
                environment.getProperty(PROFILE_PROPERTY));
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put(PROFILE_PROPERTY, profile.key());
        for (String domain : FoundationServerProfile.ALL_DOMAINS) {
            defaults.put(DOMAIN_PREFIX + domain, profile.enables(domain));
        }
        // 重复调用（如上下文刷新、测试重入）时替换而非叠加，避免默认属性源堆积
        environment.getPropertySources().remove(PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addLast(
                new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
