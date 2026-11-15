package com.foundation.starter.data.flyway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link FlywayNaming} 的命名与解析约定。 */
class FlywayNamingTest {

    @Test
    void defaultsToVPrefix() {
        assertThat(new FlywayNaming().getPrefix()).isEqualTo(FlywayNaming.DEFAULT_PREFIX);
        assertThat(FlywayNaming.DEFAULT_PREFIX).isEqualTo("V");
    }

    @Test
    void buildsConventionalFileName() {
        assertThat(new FlywayNaming().fileName(1, "foundation_schema"))
                .isEqualTo("V1__foundation_schema.sql");
        assertThat(new FlywayNaming().fileName(7, "org_unit_position_grade"))
                .isEqualTo("V7__org_unit_position_grade.sql");
    }

    @Test
    void parsesVersionAndDescription() {
        FlywayNaming naming = new FlywayNaming();

        assertThat(naming.versionOf("V31__calendar.sql")).isEqualTo(31);
        assertThat(naming.descriptionOf("V31__calendar.sql")).isEqualTo("calendar");
        assertThat(naming.versionOf("V200__app_authorization.sql")).isEqualTo(200);
    }

    @Test
    void acceptsOnlyConventionalNames() {
        FlywayNaming naming = new FlywayNaming();

        assertThat(naming.matches("V1__foundation_schema.sql")).isTrue();
        assertThat(naming.matches("V28__local_locked_fields.sql")).isTrue();
        // 无分隔符 / 描述含空格或大写 / 扩展名不符 / 前缀不符
        assertThat(naming.matches("V1-foundation.sql")).isFalse();
        assertThat(naming.matches("V1__Foundation_Schema.sql")).isFalse();
        assertThat(naming.matches("V1__foundation schema.sql")).isFalse();
        assertThat(naming.matches("V1__foundation_schema.txt")).isFalse();
        assertThat(naming.matches("U1__foundation_schema.sql")).isFalse();
        assertThat(naming.matches("__foundation_schema.sql")).isFalse();
        assertThat(naming.matches(null)).isFalse();
    }

    @Test
    void honorsConfiguredPrefix() {
        FlywayNaming naming = new FlywayNaming("U");

        assertThat(naming.matches("U5__repeatable_fix.sql")).isTrue();
        assertThat(naming.matches("V5__repeatable_fix.sql")).isFalse();
        assertThat(naming.fileName(5, "repeatable_fix")).isEqualTo("U5__repeatable_fix.sql");
    }

    @Test
    void rejectsIllegalArguments() {
        FlywayNaming naming = new FlywayNaming();

        assertThatThrownBy(() -> new FlywayNaming(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FlywayNaming("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> naming.fileName(0, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> naming.fileName(1, "Bad Name")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> naming.fileName(1, "_leading")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> naming.versionOf("legacy.sql")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> naming.descriptionOf(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
