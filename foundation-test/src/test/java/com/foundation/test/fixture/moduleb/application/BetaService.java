package com.foundation.test.fixture.moduleb.application;

import com.foundation.test.fixture.modulea.application.AlphaService;

/**
 * 规则 5 反例：模块 B 的应用服务跨模块依赖了模块 A 的内部层（application）。
 */
public class BetaService {

    private final AlphaService alphaService = new AlphaService();

    public String beta() {
        return alphaService.alpha();
    }
}
