package com.foundation.org.api.dto;

/** 变更历史来源（F-03 §2.3 {@code fdn_org_change_log.source}）。 */
public final class ChangeSource {

    /** 管理台手工操作。 */
    public static final String MANUAL = "manual";

    /** Excel 导入。 */
    public static final String IMPORT = "import";

    /** 外部来源同步（BAS-11/12/13）。 */
    public static final String SYNC = "sync";

    /** 事件驱动（Tioka 数字员工上岗 / 下岗）。 */
    public static final String EVENT = "event";

    private ChangeSource() {
    }
}
