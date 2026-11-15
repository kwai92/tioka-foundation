package com.foundation.example.arch;

import com.foundation.test.arch.FoundationArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

/**
 * F-02 BAS-24 完成标准：示例工程引用 ArchUnit 通用规则集后自动生效。
 *
 * <p>与下游项目一致，只写 {@code @ArchTest ArchTests.in(FoundationArchRules.class)} 一行，
 * 规则集即成为本工程的构建守护；测试类自身不参与分析（{@code DoNotIncludeTests}）。
 */
@AnalyzeClasses(packages = "com.foundation.example", importOptions = ImportOption.DoNotIncludeTests.class)
public class FoundationArchRulesIT {

    @ArchTest
    public static final ArchTests foundationRules = ArchTests.in(FoundationArchRules.class);
}
