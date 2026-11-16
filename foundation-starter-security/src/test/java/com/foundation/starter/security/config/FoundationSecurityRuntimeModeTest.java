package com.foundation.starter.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.core.config.CoreAutoConfiguration;
import com.foundation.starter.security.snapshot.AuthzSnapshotFetcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CM-03 守门：授权快照回源是指向 Foundation 中台的真实 HTTP 出站通道，
 * 独立运行模式（local / self-managed）下不得装配，否则「不连中台」的声明是假的。
 */
class FoundationSecurityRuntimeModeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CoreAutoConfiguration.class, FoundationSecurityAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void 接入模式保留中台授权快照通道() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AuthzSnapshotFetcher.class);
        });
    }

    @Test
    void 本地与自管模式不装配任何中台出站通道() {
        for (String mode : new String[]{"local", "self-managed"}) {
            runner.withPropertyValues("foundation.client.mode=" + mode)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean(AuthzSnapshotFetcher.class);
                        assertThat(context).doesNotHaveBean("authzSnapshotClient");
                    });
        }
    }

    @Test
    void 空模式值在启动期被拒绝_不静默连中台() {
        runner.withPropertyValues("foundation.client.mode=  ")
                .run(context -> assertThat(context).hasFailed());
    }
}
