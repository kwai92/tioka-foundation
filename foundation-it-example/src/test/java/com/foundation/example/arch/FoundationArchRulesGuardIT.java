package com.foundation.example.arch;

import com.foundation.example.ExampleApp;
import com.foundation.example.archfixture.TenantFieldFixture;
import com.foundation.test.arch.FoundationArchRules;
import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import com.tngtech.archunit.library.freeze.TextFileBasedViolationStore;
import com.tngtech.archunit.library.freeze.ViolationStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F-02 BAS-24：规则集确实能拦停违例；冻结库（FreezingArchRule）机制可用。
 */
class FoundationArchRulesGuardIT {

    /** 规则集不是空跑：tenantId 字段被 noTenantId 立即拦停。 */
    @Test
    void ruleSetStopsDeliberateViolation() {
        JavaClasses fixtureClasses = new ClassFileImporter().importClasses(TenantFieldFixture.class);

        assertThatThrownBy(() -> FoundationArchRules.noTenantId.check(fixtureClasses))
                .isInstanceOf(AssertionError.class);
    }

    /** 冻结库机制：未冻结违例立即失败；冻结后存量违例入库、规则通过，且可重复运行。 */
    @Test
    void frozenRuleBaselinesExistingViolationAndThenPasses(@TempDir Path storeDir) throws IOException {
        JavaClasses classes = new ClassFileImporter().importClasses(ExampleApp.class);

        ArchRule violation = noClasses().should().haveNameMatching(".*ExampleApp.*")
                .because("演示：示例入口类命中该规则，作为「存量违例」被冻结");

        assertThatThrownBy(() -> violation.check(classes)).isInstanceOf(AssertionError.class);

        // FreezingArchRule.evaluate 会用 ArchConfiguration 的 freeze.store.* 重新初始化库存（会覆盖显式 initialize），
        // 故通过线程级作用域把库存根目录指向临时目录并临时放开创建/更新，避免落库到项目 src/test/resources/archunit_store。
        ArchConfiguration.withThreadLocalScope(configuration -> {
            configuration.setProperty("freeze.store.default.path", storeDir.toString());
            configuration.setProperty("freeze.store.default.allowStoreCreation", "true");
            configuration.setProperty("freeze.store.default.allowStoreUpdate", "true");

            ViolationStore store = new TextFileBasedViolationStore();
            store.initialize(freezeStoreProperties(storeDir));

            ArchRule frozen = FreezingArchRule.freeze(violation).persistIn(store);
            frozen.check(classes); // 首次：违例写入库存
            frozen.check(classes); // 再次：命中库存，仍通过
        });

        try (Stream<Path> files = Files.list(storeDir)) {
            assertThat(files.filter(Files::isRegularFile)).isNotEmpty();
        }
    }

    /** ArchUnit 的 ViolationStoreFactory 会剥掉 freeze.store. 前缀后把属性交给 initialize。 */
    private static Properties freezeStoreProperties(Path storeDir) {
        Properties properties = new Properties();
        properties.setProperty("default.path", storeDir.toString());
        properties.setProperty("default.allowStoreCreation", "true");
        properties.setProperty("default.allowStoreUpdate", "true");
        return properties;
    }
}
