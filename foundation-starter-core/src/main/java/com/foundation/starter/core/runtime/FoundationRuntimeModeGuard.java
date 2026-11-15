package com.foundation.starter.core.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行模式的启动期契约提示（ADP-08）。
 *
 * <p>解决的是「配置写了却不知道有没有生效」这一类问题：模式切换会整套改变客户端装配，
 * 如果接入方只改了 {@code foundation.client.mode} 却没有提供本地实现，启动本身不会报错，
 * 但运行期第一次取字典就会失败。这里在启动阶段明确列出当前模式与接入方仍需补齐的东西。
 *
 * <p><b>刻意不 fail-fast</b>：{@code LOCAL} 模式下接入方完全可能自己注册了
 * {@code DictClient} 等 Bean（{@code @ConditionalOnMissingBean} 的既有扩展点），
 * 守卫无法在 core 层判断「是否真的缺实现」，贸然拒绝启动会误伤。因此只给可预期的告警，
 * 由 {@link #warnings()} 返回、便于单测与运维自检。
 */
public final class FoundationRuntimeModeGuard {

    private static final Logger log = LoggerFactory.getLogger(FoundationRuntimeModeGuard.class);

    /** 本地种子实现的开关；由 {@code foundation-starter-client} 读取，core 只做提示。 */
    public static final String LOCAL_SEED_PROPERTY = "foundation.client.local.enabled";

    private static final String MASTER_SWITCH = "foundation.enabled";

    private final FoundationRuntimeMode mode;
    private final Environment environment;

    public FoundationRuntimeModeGuard(FoundationRuntimeMode mode, Environment environment) {
        this.mode = mode == null ? FoundationRuntimeMode.FOUNDATION : mode;
        this.environment = environment;
    }

    /** 返回当前模式下接入方需要关注的契约提示；默认模式返回空列表。 */
    public List<String> warnings() {
        List<String> warnings = new ArrayList<>();
        if (!mode.isStandalone()) {
            return warnings;
        }
        if (environment != null && !environment.getProperty(MASTER_SWITCH, Boolean.class, Boolean.TRUE)) {
            warnings.add("foundation.enabled=false 已关闭全部 starter，运行模式 " + mode
                    + " 不会改变任何装配行为");
            return warnings;
        }
        boolean seedEnabled = environment != null
                && environment.getProperty(LOCAL_SEED_PROPERTY, Boolean.class, Boolean.FALSE);
        if (!seedEnabled) {
            warnings.add("运行模式 " + mode + " 不装配 Foundation 中台只读客户端；请自行注册 "
                    + "DictClient / ParamClient / OrgClient / PersonClient，或开启 "
                    + LOCAL_SEED_PROPERTY + "=true 使用配置种子数据");
        }
        if (mode == FoundationRuntimeMode.SELF_MANAGED) {
            warnings.add("SELF_MANAGED 模式的授权数据由接入方自管；授权导入器属 F2 范围，"
                    + "当前版本不会从中台读取授权快照");
        }
        return warnings;
    }

    /** 在启动阶段输出模式与告警；返回告警列表便于调用方复用。 */
    public List<String> validate() {
        List<String> warnings = warnings();
        log.info("Foundation 接入运行模式：{}（{}）", mode, FoundationRuntimeMode.PROPERTY);
        warnings.forEach(log::warn);
        return warnings;
    }
}
