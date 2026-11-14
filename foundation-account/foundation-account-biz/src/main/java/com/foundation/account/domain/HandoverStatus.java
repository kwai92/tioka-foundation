package com.foundation.account.domain;

/** 交接待办状态（F-04 BAS-14 §1 第 3 步）。 */
public enum HandoverStatus {

    /** 待移交。 */
    PENDING,

    /** 已移交。 */
    HANDED_OVER
}
