package com.foundation.starter.core.runtime;

import org.springframework.core.env.Environment;

import java.util.Locale;

/**
 * 接入方的运行模式（36 号规划 ADP-08 / P4「降级可配置」）。
 *
 * <p>这是<b>接入侧</b>的模式，与 {@code foundation-server} 的装配 profile
 * （{@code full/iam/common/minimal}）互不影响：profile 决定中台服务自身装配多少能力域，
 * 本枚举决定业务项目是否真的去连中台。
 *
 * <ul>
 *   <li>{@link #FOUNDATION}：默认值，经 starter 与契约接入中台，保持改造前全部行为。</li>
 *   <li>{@link #LOCAL}：应用自建身份与权限，不连中台；中台只读客户端不装配，
 *       由接入方提供本地实现，或显式开启本地种子数据。</li>
 *   <li>{@link #SELF_MANAGED}：C 类（外购 / 非 Java）自管授权，同样不连中台只读接口；
 *       授权导入器属 F2 范围，本模式先提供装配与配置的一等公民位置。</li>
 * </ul>
 *
 * <p><b>兼容基线</b>：未配置 {@code foundation.client.mode} 时按 {@link #FOUNDATION} 处理，
 * 因此存量接入方与全部既有单测零改动。
 */
public enum FoundationRuntimeMode {

    /** 默认：接入 Foundation 中台。 */
    FOUNDATION,

    /** 本地模式：不连中台，应用自建身份与权限。 */
    LOCAL,

    /** 自管模式：授权由接入方自行管理，不读中台授权与只读接口。 */
    SELF_MANAGED;

    /** 模式配置键；与 {@code foundation.client.*} 配置组同源。 */
    public static final String PROPERTY = "foundation.client.mode";

    /**
     * 宽松解析：空值按默认模式；支持大小写、连字符与下划线混用；非法值<b>直接拒绝</b>，
     * 不静默降级——避免把拼错的模式当成 {@code FOUNDATION} 而意外连上中台。
     */
    public static FoundationRuntimeMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return FOUNDATION;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "foundation" -> FOUNDATION;
            case "local" -> LOCAL;
            case "self-managed", "selfmanaged" -> SELF_MANAGED;
            default -> throw new IllegalArgumentException(
                    "Unsupported foundation.client.mode: " + raw
                            + " (expected foundation|local|self-managed)");
        };
    }

    /** 从 Spring 环境读取当前模式；{@code null} 环境按默认模式处理。 */
    public static FoundationRuntimeMode from(Environment environment) {
        return parse(environment == null ? null : environment.getProperty(PROPERTY));
    }

    /** 是否允许装配指向中台的出站通道。 */
    public boolean requiresRemoteFoundation() {
        return this == FOUNDATION;
    }

    /** 是否为「不连中台」的模式（{@code LOCAL} 与 {@code SELF_MANAGED}）。 */
    public boolean isStandalone() {
        return this == LOCAL || this == SELF_MANAGED;
    }
}
