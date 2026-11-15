package com.foundation.test.fixture.modulea.application;

import com.foundation.test.fixture.modulea.api.AlphaApi;

/**
 * 规则 5 夹具：模块 A 的内部应用服务，实现同模块的 -api 接口（同模块依赖不算违规）。
 */
public class AlphaService implements AlphaApi {

    @Override
    public String alpha() {
        return "alpha";
    }
}
