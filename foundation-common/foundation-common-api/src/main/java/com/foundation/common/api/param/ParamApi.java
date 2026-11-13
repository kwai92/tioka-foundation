package com.foundation.common.api.param;

/**
 * 参数契约（F-02 BAS-09）。
 *
 * <p>敏感参数（{@code secret=true}）密文存储、只写不读：对外 {@link #value} 一律拒绝；
 * 仅服务间内部调用 {@link #secretValue} 可解密读取。写操作按「作用域 + key」天然幂等。
 */
public interface ParamApi {

    /** 明文读；应用项优先于全局项；{@code secret=true} 抛 {@code FORBIDDEN}。 */
    String value(String appCode, String key);

    /** 内部（服务间）读：{@code secret=true} 时解密返回，非敏感参数等同 {@link #value}。 */
    String secretValue(String appCode, String key);

    /** 新建或按（作用域 + key）更新参数；每次修改记旧值与新值摘要。 */
    void set(ParamCmd cmd);
}
