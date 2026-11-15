package com.foundation.test.fixture.external;

import org.springframework.web.bind.annotation.RestController;

/**
 * 规则 2 反例：@RestController 直接持有并调用同步外部系统客户端。
 */
@RestController
public class ControllerCallingExternalSystem {

    private final FixtureApiClient client = new FixtureApiClient();

    public String invoke() {
        return client.call();
    }
}
