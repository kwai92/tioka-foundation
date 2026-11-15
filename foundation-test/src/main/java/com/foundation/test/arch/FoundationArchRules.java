package com.foundation.test.arch;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * ArchUnit 通用规则集（F-02 BAS-24 / S15 §5.3）：由各项目在测试中直接引用。
 *
 * <p>本类只依赖 archunit-junit5，不引用任何业务/框架类型：注解与基类一律用全限定名字符串，
 * 使 foundation-test 保持零编译期框架依赖（除 archunit 与容器基座外）。
 *
 * <p>对 {@code that()} 子句可能空结果的规则，末尾调用 {@code allowEmptyShould(true)}，
 * 以便在被导入的类集合与规则意图无关时也能通过（例如单测只导入某条规则的夹具）。
 */
public final class FoundationArchRules {

    private static final String BASE_PACKAGE = "com.foundation";
    private static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
    private static final String REQUIRE_PERMISSION = "com.foundation.starter.security.permission.RequirePermission";
    private static final String PUBLIC_API = "com.foundation.starter.security.permission.PublicApi";
    private static final String OBJECT_MAPPER = "com.fasterxml.jackson.databind.ObjectMapper";
    private static final String REENTRANT_LOCK = "java.util.concurrent.locks.ReentrantLock";

    private static final Set<String> DATA_OBJECT_BASES = Set.of(
            "com.foundation.starter.data.BaseDO",
            "com.foundation.starter.data.MutableRecordDO",
            "com.foundation.starter.data.AppendOnlyDO",
            "com.foundation.starter.data.ImmutableSnapshotDO");

    private static final Set<String> LAYER_MARKERS = Set.of(
            "api", "application", "domain", "infrastructure", "config", "adapter");

    /** 跨模块编译期只允许依赖对方 -api：这些层次属模块内部，禁止跨模块触碰。 */
    private static final Set<String> INTERNAL_LAYERS = Set.of(
            "application", "domain", "infrastructure", "config");

    /** DO 时间字段禁用的时间类型（UTC 语义统一用 LocalDateTime）。 */
    private static final Set<String> FORBIDDEN_TIME_FIELD_TYPES = Set.of(
            "java.time.Instant",
            "java.time.OffsetDateTime",
            "java.time.ZonedDateTime");

    /** 目录常量类的 FQCN 列表，逗号分隔；由 archunit.properties 提供。 */
    static final String PERMISSION_CATALOG_PROPERTY = "foundation.arch.permission-catalog";

    private FoundationArchRules() {
    }

    /** 规则 1：@RestController 的 public 方法必须声明 @RequirePermission 或 @PublicApi（方法级或类级）。 */
    @ArchTest
    public static final ArchRule controllersDeclarePermissionOrPublicApi =
            methods().that().areDeclaredInClassesThat().areAnnotatedWith(REST_CONTROLLER)
                    .and().arePublic()
                    .should().beAnnotatedWith(REQUIRE_PERMISSION)
                    .orShould().beAnnotatedWith(PUBLIC_API)
                    .orShould().beDeclaredInClassesThat().areAnnotatedWith(REQUIRE_PERMISSION)
                    .orShould().beDeclaredInClassesThat().areAnnotatedWith(PUBLIC_API)
                    .because("@RestController 的 public 方法必须声明 @RequirePermission 或 @PublicApi（F-02 BAS-24 / S15 §5.3）")
                    .allowEmptyShould(true);

    /** 同步外部系统客户端：..adapter.. 或 *ApiClient/*HttpClient/*FeignClient。 */
    private static final DescribedPredicate<JavaClass> SYNC_EXTERNAL_CLIENT =
            JavaClass.Predicates.resideInAPackage("..adapter..")
                    .or(JavaClass.Predicates.simpleNameEndingWith("ApiClient"))
                    .or(JavaClass.Predicates.simpleNameEndingWith("HttpClient"))
                    .or(JavaClass.Predicates.simpleNameEndingWith("FeignClient"))
                    .as("同步外部系统客户端（..adapter.. 或 *ApiClient/*HttpClient/*FeignClient）");

    /** 规则 2：请求线程禁止同步调用外部系统（Controller 只编排领域服务）。 */
    @ArchTest
    public static final ArchRule noSyncExternalCallInRequestThread =
            noClasses().that().areAnnotatedWith(REST_CONTROLLER)
                    .should().dependOnClassesThat(SYNC_EXTERNAL_CLIENT)
                    .because("请求线程禁止同步调用外部系统：Controller 只编排领域服务，外部访问经 outbox/MQ 或领域服务（F-02 BAS-24）")
                    .allowEmptyShould(true);

    private static final ArchRule NO_TENANT_ID_FIELDS =
            noFields().should().haveNameMatching("(?i)tenant_?id")
                    .because("单企业独立部署，DO 禁止 tenantId/tenant_id 字段（ADR-029 第 60 条）")
                    .allowEmptyShould(true);

    private static final ArchRule NO_TENANT_ID_CLASSES =
            noClasses().should().haveNameMatching("(?i).*sys_?tenant.*")
                    .because("禁止 sys_tenant 表/实体（ADR-029 第 60 条）")
                    .allowEmptyShould(true);

    /** 规则 3：单企业独立部署，禁止 tenant_id / sys_tenant / 租户字段。 */
    @ArchTest
    public static final ArchRule noTenantId =
            CompositeArchRule.of(NO_TENANT_ID_FIELDS).and(NO_TENANT_ID_CLASSES)
                    .because("单企业独立部署，禁止 tenant_id / sys_tenant / 租户字段（ADR-029 第 60 条；列名/迁移层由迁移基线 IT 守护）")
                    .allowEmptyShould(true);

    /** 规则 4：@RequirePermission 的权限码只能引用常量目录。 */
    @ArchTest
    public static final ArchRule permissionCodesFromCatalog =
            classes().that().areAnnotatedWith(REST_CONTROLLER)
                    .should(new PermissionCodesFromCatalogCondition())
                    .because("权限码只能引用常量目录（S15 §5.3 / F-02 BAS-24）：目录类经 archunit.properties 的 "
                            + PERMISSION_CATALOG_PROPERTY + " 声明其全限定类名（逗号分隔）")
                    .allowEmptyShould(true);

    /** 规则 5：跨模块编译期只允许依赖对方 -api。 */
    @ArchTest
    public static final ArchRule modulesDependOnlyOnApi =
            classes().that().resideInAPackage(BASE_PACKAGE + "..")
                    .should(new ModulesDependOnlyOnApiCondition())
                    .because("跨模块编译期只依赖对方 -api（S15 §5.3 / F-02 BAS-24）；application/domain/infrastructure/config 属模块内部")
                    .allowEmptyShould(true);

    /** 规则 6：禁止 new ObjectMapper()，统一注入 Spring 的 ObjectMapper。 */
    @ArchTest
    public static final ArchRule noNewObjectMapper =
            noClasses().should().callConstructor(OBJECT_MAPPER)
                    .because("禁止 new ObjectMapper()，统一注入 Spring 的 ObjectMapper（ENG-05 / F-02 BAS-24）");

    private static final DescribedPredicate<JavaClass> DATA_OBJECT =
            DescribedPredicate.describe("是 DO（继承 starter DO 基类）", FoundationArchRules::isDataObject);

    /** 规则 7：DO 时间字段只用 LocalDateTime（UTC 语义）。 */
    @ArchTest
    public static final ArchRule doTimeFieldsAreLocalDateTime =
            classes().that(DATA_OBJECT)
                    .should(new DoTimeFieldsAreLocalDateTimeCondition())
                    .because("DO 时间字段只用 LocalDateTime（UTC 语义，48-CS §4 / M0-01 / F-02 BAS-24），禁止 Instant/OffsetDateTime/ZonedDateTime")
                    .allowEmptyShould(true);

    private static final ArchRule NO_SYNCHRONIZED_METHODS =
            noMethods().that().areDeclaredInClassesThat()
                    .resideInAnyPackage("..application..", "..domain..")
                    .should().haveModifier(JavaModifier.SYNCHRONIZED)
                    .because("应用/领域层禁止 synchronized，改用 DB/Redis 锁（ADR-016 / F-02 BAS-24）")
                    .allowEmptyShould(true);

    private static final ArchRule NO_REENTRANT_LOCK_FIELDS =
            noFields().that().areDeclaredInClassesThat()
                    .resideInAnyPackage("..application..", "..domain..")
                    .should().haveRawType(REENTRANT_LOCK)
                    .because("应用/领域层禁止 ReentrantLock 做业务互斥，改用 DB/Redis 锁（ADR-016 / F-02 BAS-24）")
                    .allowEmptyShould(true);

    /** 规则 8：应用代码禁止 synchronized/ReentrantLock 做业务互斥。 */
    @ArchTest
    public static final ArchRule noSynchronizedOrReentrantLock =
            CompositeArchRule.of(NO_SYNCHRONIZED_METHODS).and(NO_REENTRANT_LOCK_FIELDS)
                    .because("应用代码禁止 synchronized/ReentrantLock 做业务互斥（ADR-016 / F-02 BAS-24）")
                    .allowEmptyShould(true);

    // ---------------------------------------------------------------------
    // 规则 4：权限码来自常量目录
    // ---------------------------------------------------------------------

    /** 校验 @RequirePermission 取值必须来自常量目录（archunit.properties 声明目录类）。 */
    private static final class PermissionCodesFromCatalogCondition extends ArchCondition<JavaClass> {

        PermissionCodesFromCatalogCondition() {
            super("只引用权限常量目录中的权限码");
        }

        @Override
        public void check(JavaClass item, ConditionEvents events) {
            Set<String> catalog = permissionCatalog();
            List<String> codes = collectRequirePermissionCodes(item);
            boolean allSatisfied = true;
            for (String code : codes) {
                // "*" 为通配（Permissions.ALL 语义），一律放行
                if ("*".equals(code)) {
                    continue;
                }
                if (catalog.isEmpty()) {
                    allSatisfied = false;
                    events.add(SimpleConditionEvent.violated(item, item.getName() + " 的权限码 " + code
                            + " 无法校验：未配置权限常量目录（archunit.properties 设置 "
                            + PERMISSION_CATALOG_PROPERTY + "=<常量类全限定名>）"));
                } else if (!catalog.contains(code)) {
                    allSatisfied = false;
                    events.add(SimpleConditionEvent.violated(item, item.getName() + " 的权限码 " + code + " 不在常量目录中"));
                }
            }
            if (allSatisfied) {
                events.add(SimpleConditionEvent.satisfied(item, item.getName() + " 的权限码均来自常量目录"));
            }
        }
    }

    /** 收集类级与方法级的 @RequirePermission 取值（已归一化为字符串列表）。 */
    private static List<String> collectRequirePermissionCodes(JavaClass item) {
        List<String> codes = new ArrayList<>();
        for (JavaAnnotation<?> annotation : item.getAnnotations()) {
            if (REQUIRE_PERMISSION.equals(annotation.getRawType().getName())) {
                codes.addAll(extractPermissionCodes(annotation));
            }
        }
        for (JavaMethod method : item.getMethods()) {
            for (JavaAnnotation<?> annotation : method.getAnnotations()) {
                if (REQUIRE_PERMISSION.equals(annotation.getRawType().getName())) {
                    codes.addAll(extractPermissionCodes(annotation));
                }
            }
        }
        return codes;
    }

    /** 把注解 value 属性归一化为字符串列表（String / String[] / Object[] / Collection）。 */
    private static List<String> extractPermissionCodes(JavaAnnotation<?> annotation) {
        Object rawValue = annotation.get("value").orElse(null);
        List<String> codes = new ArrayList<>();
        if (rawValue == null) {
            return codes;
        }
        if (rawValue instanceof String single) {
            codes.add(single);
        } else if (rawValue instanceof Object[] array) {
            for (Object element : array) {
                codes.add(String.valueOf(element));
            }
        } else if (rawValue instanceof Collection<?> collection) {
            for (Object element : collection) {
                codes.add(String.valueOf(element));
            }
        }
        return codes;
    }

    /**
     * 读取 archunit.properties 声明的目录类，收集其 public static final String 常量。
     *
     * <p>目录规模很小，每次重新计算、不做缓存，避免 JVM 级 {@link ArchConfiguration} 单例的读写顺序问题。
     */
    static Set<String> permissionCatalog() {
        Set<String> catalog = new LinkedHashSet<>();
        String configured = ArchConfiguration.get().getPropertyOrDefault(PERMISSION_CATALOG_PROPERTY, "");
        for (String candidate : configured.split(",")) {
            String fqcn = candidate.trim();
            if (fqcn.isEmpty()) {
                continue;
            }
            collectCatalogConstants(fqcn, catalog);
        }
        return catalog;
    }

    /** 加载目录类并收集其可见的字符串常量；任何异常都视为该类缺失（不抛测试异常）。 */
    private static void collectCatalogConstants(String fqcn, Set<String> catalog) {
        try {
            Class<?> catalogClass = Class.forName(fqcn);
            for (Field field : catalogClass.getFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)
                        && field.getType() == String.class) {
                    Object value = field.get(null);
                    if (value instanceof String constant) {
                        catalog.add(constant);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // 目录类不存在或不可读：按空目录处理，由 check 记违规并给出配置提示
        }
    }

    // ---------------------------------------------------------------------
    // 规则 5：跨模块只依赖对方 -api
    // ---------------------------------------------------------------------

    /** 跨模块编译期只允许依赖对方 -api。 */
    private static final class ModulesDependOnlyOnApiCondition extends ArchCondition<JavaClass> {

        ModulesDependOnlyOnApiCondition() {
            super("跨模块只依赖对方 -api");
        }

        @Override
        public void check(JavaClass item, ConditionEvents events) {
            PackageParts itemParts = split(item.getPackageName());
            if (itemParts.module() == null) {
                events.add(SimpleConditionEvent.satisfied(item, item.getName() + " 不在 " + BASE_PACKAGE + " 包下，跳过"));
                return;
            }
            boolean satisfied = true;
            for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                PackageParts targetParts = split(dependency.getTargetClass().getPackageName());
                if (targetParts.module() == null || targetParts.module().equals(itemParts.module())) {
                    continue;
                }
                if (targetParts.layer() == null
                        || "api".equals(targetParts.layer())
                        || !INTERNAL_LAYERS.contains(targetParts.layer())) {
                    continue;
                }
                satisfied = false;
                events.add(SimpleConditionEvent.violated(item,
                        item.getName() + " 跨模块依赖了 " + dependency.getTargetClass().getName()
                                + "（" + dependency.getDescription() + "）；只允许依赖对方 -api"));
            }
            if (satisfied) {
                events.add(SimpleConditionEvent.satisfied(item, item.getName() + " 跨模块依赖均指向 -api"));
            }
        }
    }

    /** 包名解析结果：module 为 null 表示不在 com.foundation 包下。 */
    private record PackageParts(String module, String layer) {
    }

    /**
     * 按「模块 = 首个层次标记之前的段」「层次 = 首个层次标记」解析包名。
     *
     * <p>{@code module} 为 {@code null}：不在 {@code com.foundation} 包下；无层次标记时 {@code layer} 为 {@code null}。
     */
    private static PackageParts split(String packageName) {
        if (!packageName.equals(BASE_PACKAGE) && !packageName.startsWith(BASE_PACKAGE + ".")) {
            return new PackageParts(null, null);
        }
        String rest = packageName.equals(BASE_PACKAGE) ? "" : packageName.substring(BASE_PACKAGE.length() + 1);
        if (rest.isEmpty()) {
            return new PackageParts("", null);
        }
        String[] segments = rest.split("\\.");
        int layerIndex = -1;
        for (int i = 0; i < segments.length; i++) {
            if (LAYER_MARKERS.contains(segments[i])) {
                layerIndex = i;
                break;
            }
        }
        if (layerIndex < 0) {
            return new PackageParts(String.join(".", segments), null);
        }
        String module = String.join(".", Arrays.copyOfRange(segments, 0, layerIndex));
        return new PackageParts(module, segments[layerIndex]);
    }

    // ---------------------------------------------------------------------
    // 规则 7：DO 时间字段只用 LocalDateTime
    // ---------------------------------------------------------------------

    private static boolean isDataObject(JavaClass javaClass) {
        if (DATA_OBJECT_BASES.contains(javaClass.getName())) {
            return true;
        }
        for (JavaClass superClass : javaClass.getAllRawSuperclasses()) {
            if (DATA_OBJECT_BASES.contains(superClass.getName())) {
                return true;
            }
        }
        return false;
    }

    /** DO 时间字段只用 LocalDateTime。 */
    private static final class DoTimeFieldsAreLocalDateTimeCondition extends ArchCondition<JavaClass> {

        DoTimeFieldsAreLocalDateTimeCondition() {
            super("时间字段只用 LocalDateTime");
        }

        @Override
        public void check(JavaClass item, ConditionEvents events) {
            boolean satisfied = true;
            for (JavaField field : item.getFields()) {
                String typeName = field.getRawType().getName();
                if (FORBIDDEN_TIME_FIELD_TYPES.contains(typeName)) {
                    satisfied = false;
                    events.add(SimpleConditionEvent.violated(item, item.getName() + " 的时间字段 " + field.getName()
                            + " 使用 " + typeName + "，必须改为 LocalDateTime（UTC 语义）"));
                }
            }
            if (satisfied) {
                events.add(SimpleConditionEvent.satisfied(item, item.getName() + " 的时间字段均为 LocalDateTime"));
            }
        }
    }
}
