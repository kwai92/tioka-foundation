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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADP-01 补强：对外 REST 接口「响应形状」的冻结基线（36 号规划 §6「契约版本」、S15 §5.3）。
 *
 * <p>为什么需要本测试（消除既有假绿）：{@link ApiInventoryContractTest} 只冻结
 * {@code HTTP方法 + 路径}，DTO 的字段改名、字段类型变更、record 组件增删、枚举值增删
 * 都不会让它变红；模板 CI 的 openapi.json diff 在无基线时条件跳过（exit 0），
 * 完整 OpenAPI 又依赖 springdoc 运行时（需拉起数据库/中间件，违背契约测试离线原则）。
 * 于是「响应 schema 级 breaking change」此前全链路检测不到。本测试用纯反射补上这一层：
 * 不启 Spring context、不依赖任何中间件，与 {@link ApiInventoryContractTest} 同模式。
 *
 * <p>冻结内容分两段，均为确定性排序（端点按字典序、类型按名字典序、字段按名字典序），
 * 与源文件内方法/字段的书写顺序无关：
 * <ol>
 *   <li><b>端点段</b>：{@code HTTP方法 路径 -> 完整泛型返回类型}。端点删除、路径变更、
 *       返回类型变更（含泛型实参，如 {@code List<PersonView>} 改 {@code PageResult<PersonView>}）即红。</li>
 *   <li><b>类型段</b>：从全部端点返回类型出发，递归展开所有 {@code com.foundation} 自有类型
 *       （record 组件、类字段含父类链、枚举常量集、泛型形参）。字段改名/改类型/增删、
 *       枚举值增删即红。JDK/Spring 类型只记全限定名不展开（其形状不由本仓承诺）。</li>
 * </ol>
 *
 * <p>边界与诚实声明：返回 {@code Map<String, Object>} 的端点（现状约 13 个）在本基线中
 * 只能冻结为字面类型串，<b>其内部键值形状仍不可检测</b>——这是既有契约弱点，本测试只如实
 * 冻结、不掩盖也不治理；治理需另行立项（把 Map 返回改为具名 DTO）。
 *
 * <p>刷新方式（新增接口/合法演进后，须评审并在发版说明登记，同 ADP-01 约定）：
 * {@code mvn -pl foundation-server test -Dtest=ResponseShapeContractTest -Dfoundation.contract.update=true}
 */
class ResponseShapeContractTest {

    private static final Path BASELINE = Path.of("src/test/resources/contracts/api-response-shapes-v1.txt");
    private static final String UPDATE_FLAG = "foundation.contract.update";
    private static final String SCAN_PACKAGE = "com.foundation";
    private static final String HEADER =
            "# api-response-shapes-v1 — 对外 REST 端点返回类型与 com.foundation DTO 形状冻结基线。\n"
                    + "# 由 ResponseShapeContractTest 生成；勿手工编辑。刷新：mvn -pl foundation-server test \\\n"
                    + "#   -Dtest=ResponseShapeContractTest -Dfoundation.contract.update=true\n";

    @Test
    void 对外接口响应形状与冻结基线一致() throws Exception {
        String current = snapshot();
        if (Boolean.getBoolean(UPDATE_FLAG)) {
            Files.createDirectories(BASELINE.getParent());
            Files.writeString(BASELINE, current, StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.exists(BASELINE),
                "响应形状基线缺失，请先以 -Dfoundation.contract.update=true 生成：" + BASELINE);
        assertEquals(Files.readString(BASELINE, StandardCharsets.UTF_8), current,
                "对外接口响应形状发生变化。DTO 字段改名/改类型/增删、record 组件变更、枚举值增删、"
                        + "端点返回类型变更均属破坏性变更，需评审；合法演进请刷新基线并在发版说明登记"
                        + "（36 号规划 §6、S15 §5.3）。");
    }

    // ---------------------------------------------------------------- 快照生成

    private String snapshot() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        // 与 ApiInventoryContractTest 同口径：本仓多数控制器按「@ResponseBody + 类级 @RequestMapping
        // + AutoConfiguration 显式 @Bean」装配，不标 @RestController；三种入口都纳入，避免漏扫。
        scanner.addIncludeFilter(new AnnotationTypeFilter(ResponseBody.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(RequestMapping.class));

        Set<String> endpoints = new TreeSet<>();
        Set<Class<?>> roots = new LinkedHashSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(SCAN_PACKAGE)) {
            Class<?> controller = Class.forName(definition.getBeanClassName());
            Set<String> bases = paths(
                    AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class));
            for (Method method : controller.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                Type returnType = method.getGenericReturnType();
                collectFoundationTypes(returnType, roots);
                String typeText = typeString(returnType);
                for (String base : bases) {
                    for (String sub : paths(mapping)) {
                        for (String httpMethod : httpMethods(mapping)) {
                            endpoints.add(httpMethod + " " + normalize(base, sub) + " -> " + typeText);
                        }
                    }
                }
            }
        }
        if (endpoints.isEmpty()) {
            throw new IllegalStateException("未扫描到任何 REST 接口，扫描配置或 classpath 有误");
        }

        StringBuilder sb = new StringBuilder(HEADER);
        sb.append("\n## endpoints\n");
        for (String endpoint : endpoints) {
            sb.append(endpoint).append('\n');
        }

        sb.append("\n## types\n");
        TreeMap<String, String> typeSections = expandTypes(roots);
        if (typeSections.isEmpty()) {
            throw new IllegalStateException("端点返回类型中未解析出任何 com.foundation DTO，展开逻辑有误");
        }
        for (String section : typeSections.values()) {
            sb.append(section);
        }
        return sb.toString();
    }

    /**
     * 从根类型出发 BFS 展开全部 com.foundation 类型（record/类/枚举），产出
     * {@code TYPE 全限定名} 开头的确定性文本段；带环保护（visited 集）。
     */
    private TreeMap<String, String> expandTypes(Set<Class<?>> roots) {
        TreeMap<String, String> sections = new TreeMap<>();
        Set<Class<?>> visited = new LinkedHashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            Class<?> type = queue.poll();
            if (!visited.add(type)) {
                continue;
            }
            sections.put(type.getName(), describe(type, queue));
        }
        return sections;
    }

    private String describe(Class<?> type, Deque<Class<?>> queue) {
        StringBuilder sb = new StringBuilder();
        sb.append("TYPE ").append(type.getName());
        sb.append(" (").append(kindOf(type)).append(")");
        TypeVariable<?>[] params = type.getTypeParameters();
        if (params.length > 0) {
            List<String> names = new ArrayList<>();
            for (TypeVariable<?> p : params) {
                names.add(p.getName());
            }
            sb.append(" <").append(String.join(", ", names)).append('>');
        }
        sb.append('\n');

        if (type.isEnum()) {
            List<String> constants = new ArrayList<>();
            for (Object constant : type.getEnumConstants()) {
                constants.add(((Enum<?>) constant).name());
            }
            java.util.Collections.sort(constants);
            sb.append("  ENUM ").append(String.join(", ", constants)).append('\n');
            return sb.toString();
        }

        List<String> fields = new ArrayList<>();
        if (type.isRecord()) {
            for (RecordComponent component : safeRecordComponents(type)) {
                fields.add("  FIELD " + component.getName() + ": " + typeString(component.getGenericType()));
                collectFoundationTypes(component.getGenericType(), queue);
            }
        } else {
            // 普通类：字段含父类链（DTO 可能继承公共基类）；static/synthetic 不入契约。
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : safeDeclaredFields(c)) {
                    if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    fields.add("  FIELD " + field.getName() + ": " + typeString(field.getGenericType()));
                    collectFoundationTypes(field.getGenericType(), queue);
                }
            }
        }
        // 字段按名字典序：与书写顺序无关，保证基线确定性。
        fields.sort(String::compareTo);
        for (String field : fields) {
            sb.append(field).append('\n');
        }
        return sb.toString();
    }

    private String kindOf(Class<?> type) {
        if (type.isRecord()) {
            return "record";
        }
        if (type.isEnum()) {
            return "enum";
        }
        if (type.isInterface()) {
            return "interface";
        }
        return "class";
    }

    /**
     * 可选依赖（如 RocketMQ/MyBatis-Plus 为 optional）可能使个别字段类型无法加载；
     * 不静默吞掉——落成确定性的 UNRESOLVED 行，classpath 变化时基线会显式漂移而非漏检。
     */
    private RecordComponent[] safeRecordComponents(Class<?> type) {
        try {
            return type.getRecordComponents();
        } catch (NoClassDefFoundError | TypeNotPresentException e) {
            return new RecordComponent[0];
        }
    }

    private Field[] safeDeclaredFields(Class<?> type) {
        try {
            return type.getDeclaredFields();
        } catch (NoClassDefFoundError | TypeNotPresentException e) {
            return new Field[0];
        }
    }

    // ---------------------------------------------------------------- 类型工具

    private void collectFoundationTypes(Type type, Collection<Class<?>> out) {
        collectFoundationTypes(type, out, new LinkedHashSet<>());
    }

    private void collectFoundationTypes(Type type, Collection<Class<?>> out, Set<Type> seen) {
        if (type == null || !seen.add(type)) {
            return;
        }
        if (type instanceof Class<?> c) {
            if (c.isArray()) {
                collectFoundationTypes(c.getComponentType(), out, seen);
                return;
            }
            if (c.getName().startsWith(SCAN_PACKAGE + ".")) {
                out.add(c);
            }
        } else if (type instanceof ParameterizedType p) {
            collectFoundationTypes(p.getRawType(), out, seen);
            for (Type argument : p.getActualTypeArguments()) {
                collectFoundationTypes(argument, out, seen);
            }
        } else if (type instanceof GenericArrayType g) {
            collectFoundationTypes(g.getGenericComponentType(), out, seen);
        } else if (type instanceof WildcardType w) {
            for (Type bound : w.getUpperBounds()) {
                collectFoundationTypes(bound, out, seen);
            }
            for (Type bound : w.getLowerBounds()) {
                collectFoundationTypes(bound, out, seen);
            }
        }
    }

    /** 把任意 Type 序列化为确定性文本：全限定名 + 完整泛型实参（泛型实参是契约的一部分）。 */
    private static String typeString(Type type) {
        if (type instanceof Class<?> c) {
            return c.isArray() ? typeString(c.getComponentType()) + "[]" : c.getName();
        }
        if (type instanceof ParameterizedType p) {
            StringBuilder sb = new StringBuilder(typeString(p.getRawType())).append('<');
            Type[] arguments = p.getActualTypeArguments();
            for (int i = 0; i < arguments.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(typeString(arguments[i]));
            }
            return sb.append('>').toString();
        }
        if (type instanceof TypeVariable<?> v) {
            return v.getName();
        }
        if (type instanceof GenericArrayType g) {
            return typeString(g.getGenericComponentType()) + "[]";
        }
        if (type instanceof WildcardType w) {
            Type[] lower = w.getLowerBounds();
            if (lower.length > 0) {
                return "? super " + typeString(lower[0]);
            }
            Type[] upper = w.getUpperBounds();
            if (upper.length == 1 && upper[0] == Object.class) {
                return "?";
            }
            return "? extends " + typeString(upper[0]);
        }
        return "UNRESOLVED(" + type + ")";
    }

    // ---------------------------------------------------------------- 与 ApiInventoryContractTest 同口径的路径工具

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
