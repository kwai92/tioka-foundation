package com.foundation.app.api;

/**
 * 应用权限清单版本读写（F-05 BAS-05 §清单导入规则）。
 *
 * <p>{@code fdn_app.manifest_version} 由应用登记（BAS-04）持有，权限中心经本契约跨模块读写，
 * 保持「跨模块只依赖对方 {@code -api}」的硬约束。
 */
public interface AppManifestVersionApi {

    /** 当前生效的清单版本；应用不存在时抛 {@code NOT_FOUND}。 */
    long currentVersion(String appCode);

    /**
     * 清单导入成功后把版本推进到 {@code newVersion}。
     *
     * <p>由调用方保证 {@code newVersion} 大于当前版本（版本只能前进）。
     */
    void advance(String appCode, long newVersion, String operator);
}
