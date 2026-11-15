package com.foundation.test.fixture.moduleb.application;

import com.foundation.test.fixture.modulea.api.AlphaApi;

/**
 * 规则 5 正例：模块 B 的应用服务只跨模块依赖模块 A 的 -api。
 */
public class CleanBetaService {

    private final AlphaApi alphaApi;

    public CleanBetaService(AlphaApi alphaApi) {
        this.alphaApi = alphaApi;
    }

    public String beta() {
        return alphaApi.alpha();
    }
}
