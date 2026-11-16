package com.foundation.server.contract;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADP-01：对外 REST 接口清单的冻结基线（36 号规划 §6「契约版本」）。
 *
 * <p>扫描 classpath 上全部 {@code com.foundation} 的 {@link RestController}，把
 * {@code HTTP方法 + 路径} 汇总成有序清单，与基线逐字比对。删除接口、改路径、改 HTTP 方法
 * 都会让本测试变红，从而阻止破坏性变更无声合入；新增接口也需显式刷新基线并评审。
 *
 * <p>这是不依赖数据库/中间件的 v1 最小契约基线；完整 OpenAPI 文档由 springdoc 在运行时生成，
 * 后续接入方契约测试可直接消费本清单。刷新方式：
 * {@code mvn -pl foundation-server test -Dtest=ApiInventoryContractTest -Dfoundation.contract.update=true}。
 */
class ApiInventoryContractTest {

    private static final Path BASELINE = Path.of("src/test/resources/contracts/api-inventory-v1.txt");
    private static final String UPDATE_FLAG = "foundation.contract.update";
    private static final String SCAN_PACKAGE = "com.foundation";

    @Test
    void 对外接口清单与冻结基线一致() throws Exception {
        String current = inventory();
        if (Boolean.getBoolean(UPDATE_FLAG)) {
            Files.createDirectories(BASELINE.getParent());
            Files.writeString(BASELINE, current, StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.exists(BASELINE),
                "接口清单基线缺失，请先以 -Dfoundation.contract.update=true 生成：" + BASELINE);
        assertEquals(Files.readString(BASELINE, StandardCharsets.UTF_8), current,
                "对外接口清单发生变化。删除/改名接口、改 HTTP 方法属破坏性变更，"
                        + "新增接口需评审后刷新基线并在发版说明中登记（36 号规划 §6）。");
    }

    private String inventory() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        // 本仓库多数控制器按「@ResponseBody + 类级 @RequestMapping + AutoConfiguration 显式 @Bean」
        // 装配，不标 @RestController；三种入口都纳入扫描，避免漏掉对外接口。
        scanner.addIncludeFilter(new AnnotationTypeFilter(ResponseBody.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(RequestMapping.class));
        Set<String> endpoints = new TreeSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(SCAN_PACKAGE)) {
            Class<?> controller = Class.forName(definition.getBeanClassName());
            Set<String> bases = paths(
                    AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class));
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                for (String base : bases) {
                    for (String sub : paths(mapping)) {
                        for (String httpMethod : httpMethods(mapping)) {
                            endpoints.add(httpMethod + " " + normalize(base, sub));
                        }
                    }
                }
            }
        }
        if (endpoints.isEmpty()) {
            throw new IllegalStateException("未扫描到任何 REST 接口，扫描配置或 classpath 有误");
        }
        return String.join("\n", endpoints) + "\n";
    }

    /** 类级注解可为空（表示无基路径）；方法级 path 与 value 取并集。 */
    private Set<String> paths(RequestMapping mapping) {
        Set<String> result = new LinkedHashSet<>();
        if (mapping == null) {
            result.add("");
            return result;
        }
        for (String path : mapping.path()) {
            result.add(path);
        }
        for (String path : mapping.value()) {
            result.add(path);
        }
        if (result.isEmpty()) {
            result.add("");
        }
        return result;
    }

    private Set<String> httpMethods(RequestMapping mapping) {
        Set<String> result = new LinkedHashSet<>();
        for (RequestMethod method : mapping.method()) {
            result.add(method.name());
        }
        if (result.isEmpty()) {
            result.add("ANY");
        }
        return result;
    }

    private String normalize(String base, String sub) {
        String joined = (base + "/" + sub).replaceAll("/{2,}", "/");
        if (!joined.startsWith("/")) {
            joined = "/" + joined;
        }
        if (joined.length() > 1 && joined.endsWith("/")) {
            joined = joined.substring(0, joined.length() - 1);
        }
        return joined;
    }
}
