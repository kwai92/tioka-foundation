package com.foundation.authz.domain;

/**
 * 清单声明的权限码（F-05 BAS-05 §清单导入规则）。
 *
 * @param code      权限码（{@code 资源:动作}）
 * @param name      说明，可空
 * @param riskLevel 风险级别
 */
public record ManifestPermission(String code, String name, RiskLevel riskLevel) {
}
