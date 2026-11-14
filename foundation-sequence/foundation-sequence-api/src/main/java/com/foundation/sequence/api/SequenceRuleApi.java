package com.foundation.sequence.api;

import java.util.List;

/**
 * 编号规则管理契约（F-09 BAS-18 §18.2）。
 *
 * <p>需权限码 {@code fdn:sequence:admin}。
 */
public interface SequenceRuleApi {

    /** 规则列表。 */
    List<SequenceRuleView> rules();

    /** 新建规则（{@code code} 全局唯一，重复时冲突）；返回含预览样例的规则。 */
    SequenceRuleView create(SequenceRuleCmd cmd, String operator);

    /** 修改规则（按业务码值定位）；返回含预览样例的规则。 */
    SequenceRuleView update(String code, SequenceRuleCmd cmd, String operator);
}
