package com.foundation.starter.client.local;

import com.foundation.starter.client.param.ParamClient;

import java.util.Optional;

/**
 * 参数读取的本地种子实现（ADP-08）：不发起任何 HTTP 调用。
 *
 * <p>延续 F-02 §BAS-09 的「敏感参数只写不读」：配置在
 * {@code foundation.client.local.secret-params} 中的键一律返回空，即使种子里给了值。
 */
public class LocalParamClient implements ParamClient {

    private final LocalFoundationData data;

    public LocalParamClient(LocalFoundationData data) {
        this.data = data;
    }

    @Override
    public Optional<String> value(String key) {
        return data.paramValue(key);
    }
}
