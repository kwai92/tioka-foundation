package com.foundation.authz.api;

/**
 * 应用目录契约（F-05 BAS-27 §目录）。
 *
 * <p>返回子系统下属的模块/功能目录树，节点启用状态受功能开关影响。
 */
public interface DirectoryApi {

    /** 返回应用的子系统/模块/功能目录树。 */
    DirectoryView directory(String appCode);
}
