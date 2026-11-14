package com.foundation.org.api;

/**
 * 人员生命周期写契约（F-04 BAS-14）：离职 / 停用链路的写入口。
 *
 * <p>{@link PersonApi} 保持只读（F-03 §5），本接口只暴露状态流转，不暴露档案字段写入；
 * 状态变更由本域写变更日志并发 {@code fdn.person.disabled}（消费端按事件 ID 幂等）。
 */
public interface PersonLifecycleApi {

    /**
     * 离职：状态置 {@code LEFT}、写变更日志，并发 {@code fdn.person.disabled}
     * （{@code changeType=LEFT}，对应 F-04 §BAS-14 载荷里的 {@code reason=离职}）。
     *
     * <p>幂等：已是 {@code LEFT} 时不再变更、不重复发事件。
     *
     * @param operator 操作人（审计），可空
     */
    void offboard(String personId, String operator);
}
