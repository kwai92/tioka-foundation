package com.foundation.starter.data.config;

import com.foundation.starter.data.flyway.FlywayNaming;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link FoundationMigrationAutoConfiguration} 的条件装配与 {@code foundation.data.*} 绑定。 */
class FoundationMigrationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FoundationMigrationAutoConfiguration.class));

    @Test
    void bindsDefaultPrefix() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(FoundationDataProperties.class);
            assertThat(context).hasSingleBean(FlywayNaming.class);
            assertThat(context.getBean(FoundationDataProperties.class).getFlywayPrefix()).isEqualTo("V");
            assertThat(context.getBean(FlywayNaming.class).getPrefix()).isEqualTo("V");
        });
    }

    @Test
    void bindsOverriddenPrefix() {
        runner.withPropertyValues("foundation.data.flyway-prefix=U").run(context -> {
            assertThat(context.getBean(FoundationDataProperties.class).getFlywayPrefix()).isEqualTo("U");
            assertThat(context.getBean(FlywayNaming.class).matches("U2__dict_param.sql")).isTrue();
        });
    }

    @Test
    void staysOffWhenFoundationDisabled() {
        runner.withPropertyValues("foundation.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FlywayNaming.class));
    }

    @Test
    void backsOffWhenApplicationProvidesItsOwnNaming() {
        runner.withBean(FlywayNaming.class, () -> new FlywayNaming("R"))
                .run(context -> assertThat(context.getBean(FlywayNaming.class).getPrefix()).isEqualTo("R"));
    }
}
