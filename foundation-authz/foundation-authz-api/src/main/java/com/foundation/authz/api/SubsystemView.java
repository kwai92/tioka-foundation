package com.foundation.authz.api;

/**
 * 子系统视图（F-05 BAS-27 §子系统）。
 *
 * <p>子系统与应用（BAS-04 {@code fdn_app}）以 {@code app_id} 1:1 关联。
 *
 * @param id            子系统 ID（{@code sub_{ulid}}）
 * @param appId         所属应用 ID
 * @param appCode       应用编码
 * @param name          子系统名称
 * @param authMode      授权模式：{@code CONCENTRATED} / {@code GRADED} / {@code HYBRID} / {@code SELF}
 * @param portalVisible 是否在门户可见
 * @param adminUserId   子系统管理员用户 ID，可空
 */
public record SubsystemView(String id,
                            String appId,
                            String appCode,
                            String name,
                            String authMode,
                            boolean portalVisible,
                            String adminUserId) {
}
