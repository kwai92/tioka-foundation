package com.foundation.common.api.event;

/** 公共域事件名（S15 §14.2：事件名固定，事件体只带 ID、版本、变化类型）。 */
public final class CommonEventTypes {

    /** 字典类型/项变更。 */
    public static final String DICT_CHANGED = "fdn.dict.changed";

    /** 参数变更。 */
    public static final String PARAM_CHANGED = "fdn.param.changed";

    private CommonEventTypes() {
    }
}
