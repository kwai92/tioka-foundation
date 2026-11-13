package com.foundation.authz.domain;

/** 菜单与权限码生命周期状态（F-05 BAS-05 §清单导入规则）：清单中消失的项标 {@link #OFFLINE}。 */
public final class LifecycleStatus {

    /** 在线：清单声明中存在。 */
    public static final String ACTIVE = "ACTIVE";

    /** 下线：清单中已消失，保留至保留期结束后清理，且从授权快照中剔除。 */
    public static final String OFFLINE = "OFFLINE";

    private LifecycleStatus() {
    }
}
