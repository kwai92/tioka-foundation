package com.foundation.starter.client.param;

import java.util.Optional;

/**
 * 参数读取（F-07 §7）。
 *
 * <p>**敏感参数只写不读**：{@code secret=true} 时拒绝（返回空），密文只允许 Foundation
 * 服务间内部接口解密（F-02 §BAS-09）。
 */
public interface ParamClient {

    /** 取参数值；敏感参数拒绝并返回空。 */
    Optional<String> value(String key);
}
