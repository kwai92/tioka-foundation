package com.foundation.authz.api;

import java.util.List;
import java.util.Optional;

/**
 * 子系统契约（F-05 BAS-27 §子系统）。
 *
 * <p>子系统是目录与授权模式的承载面；授权模式见 BAS-27 §四种模式，模式切换规则见 BAS-27 §模式切换。
 */
public interface SubsystemApi {

    /** 子系统列表。 */
    List<SubsystemView> list();

    /** 按应用编码查找子系统。 */
    Optional<SubsystemView> find(String appCode);

    /**
     * 变更授权模式。
     *
     * <p>自管（{@code SELF}）改为集中（{@code CONCENTRATED}）前必须先完成一次性导入（BAS-27 §模式切换），
     * 否则返回 {@code CONFLICT}。
     *
     * @param appCode  应用编码
     * @param authMode 目标授权模式
     * @param operator 操作人
     */
    SubsystemView changeAuthMode(String appCode, String authMode, String operator);
}
