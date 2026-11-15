package com.foundation.starter.client.local;

import com.foundation.starter.client.config.FoundationClientProperties;
import com.foundation.starter.client.dict.DictItem;
import com.foundation.starter.client.org.OrgUnit;
import com.foundation.starter.client.org.Person;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 本地种子数据的只读视图（ADP-08）。
 *
 * <p>把 {@code foundation.client.local.*} 的松散配置一次性转换成不可变查找结构，
 * 四个本地客户端共享同一份视图；组织树的「含下级」查询也在这里统一展开，
 * 避免每个客户端各写一遍遍历逻辑。
 *
 * <p>这只是<b>配置种子</b>，不是缓存或副本：进程内只读、不回源、不订阅事件，
 * 因此 {@code local} / {@code self-managed} 模式下完全不需要 Redis 与 RocketMQ。
 */
public final class LocalFoundationData {

    private final Map<String, List<DictItem>> dict;
    private final Map<String, String> param;
    private final Set<String> secretParams;
    private final Map<String, OrgUnit> orgByCode;
    private final Map<String, List<OrgUnit>> childrenByParentId;
    private final Map<String, Person> personById;
    private final Map<String, Person> personByEmployeeNo;
    private final Map<String, List<Person>> personByOrgCode;

    private LocalFoundationData(Builder builder) {
        this.dict = Map.copyOf(builder.dict);
        this.param = Map.copyOf(builder.param);
        this.secretParams = Set.copyOf(builder.secretParams);
        this.orgByCode = Map.copyOf(builder.orgByCode);
        Map<String, List<OrgUnit>> children = new LinkedHashMap<>();
        builder.childrenByParentId.forEach((key, value) -> children.put(key, List.copyOf(value)));
        this.childrenByParentId = Map.copyOf(children);
        this.personById = Map.copyOf(builder.personById);
        this.personByEmployeeNo = Map.copyOf(builder.personByEmployeeNo);
        Map<String, List<Person>> byOrg = new LinkedHashMap<>();
        builder.personByOrgCode.forEach((key, value) -> byOrg.put(key, List.copyOf(value)));
        this.personByOrgCode = Map.copyOf(byOrg);
    }

    /** 从配置构建视图；{@code appCode} 用于「本应用字典项优先」，可为 {@code null}。 */
    public static LocalFoundationData from(FoundationClientProperties.Local local, String appCode) {
        Builder builder = new Builder(appCode);
        if (local == null) {
            return builder.build();
        }
        local.getDict().forEach((typeCode, seeds) -> {
            List<DictItem> items = new ArrayList<>();
            if (seeds != null) {
                for (FoundationClientProperties.DictSeed seed : seeds) {
                    if (seed == null) {
                        continue;
                    }
                    items.add(new DictItem(typeCode, seed.getItemCode(), seed.getItemName(),
                            seed.getSort(), seed.getAppCode()));
                }
            }
            builder.dict.put(typeCode, builder.sortDict(items));
        });
        builder.param.putAll(local.getParam());
        builder.secretParams.addAll(local.getSecretParams());
        for (FoundationClientProperties.OrgUnitSeed seed : local.getOrgUnits()) {
            if (seed == null || seed.getCode() == null) {
                continue;
            }
            OrgUnit unit = new OrgUnit(seed.getId(), seed.getCode(), seed.getName(),
                    seed.getParentId(), seed.getSort());
            builder.orgByCode.put(unit.code(), unit);
            builder.childrenByParentId
                    .computeIfAbsent(unit.parentId() == null ? "" : unit.parentId(), key -> new ArrayList<>())
                    .add(unit);
        }
        for (FoundationClientProperties.PersonSeed seed : local.getPersons()) {
            if (seed == null || seed.getId() == null) {
                continue;
            }
            Person person = new Person(seed.getId(), seed.getEmployeeNo(), seed.getName(),
                    seed.getAccountId(), seed.getPrimaryOrgUnit(), seed.getStatus());
            builder.personById.put(person.id(), person);
            if (person.employeeNo() != null) {
                builder.personByEmployeeNo.put(person.employeeNo(), person);
            }
            if (person.primaryOrgUnit() != null) {
                builder.personByOrgCode
                        .computeIfAbsent(person.primaryOrgUnit(), key -> new ArrayList<>())
                        .add(person);
            }
        }
        return builder.build();
    }

    /** 字典项：不存在的类型返回空列表（业务空结果，不是故障）。 */
    public List<DictItem> dictItems(String typeCode) {
        return dict.getOrDefault(typeCode, List.of());
    }

    /** 参数值：敏感键或未配置的键返回空（延续 F-02 §BAS-09「敏感参数只写不读」）。 */
    public Optional<String> paramValue(String key) {
        if (key == null || secretParams.contains(key)) {
            return Optional.empty();
        }
        return Optional.ofNullable(param.get(key));
    }

    public Optional<OrgUnit> orgByCode(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(orgByCode.get(code));
    }

    /** 直接下级组织单元，按 sort 稳定排序。 */
    public List<OrgUnit> children(String orgCode) {
        OrgUnit parent = orgByCode.get(orgCode);
        if (parent == null || parent.id() == null) {
            return List.of();
        }
        List<OrgUnit> result = new ArrayList<>(childrenByParentId.getOrDefault(parent.id(), List.of()));
        result.sort(Comparator.comparingInt(OrgUnit::sort));
        return List.copyOf(result);
    }

    public Optional<Person> personById(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(personById.get(id));
    }

    public Optional<Person> personByEmployeeNo(String employeeNo) {
        return employeeNo == null ? Optional.empty() : Optional.ofNullable(personByEmployeeNo.get(employeeNo));
    }

    /** 组织下人员；{@code includeSub} 为真时按 parentId 递归展开整棵子树。 */
    public List<Person> personsInOrg(String orgCode, boolean includeSub) {
        Set<String> codes = new LinkedHashSet<>();
        codes.add(orgCode);
        if (includeSub) {
            OrgUnit root = orgByCode.get(orgCode);
            if (root != null && root.id() != null) {
                Deque<String> queue = new ArrayDeque<>();
                Set<String> visitedIds = new LinkedHashSet<>();
                queue.addLast(root.id());
                visitedIds.add(root.id());
                while (!queue.isEmpty()) {
                    String parentId = queue.removeFirst();
                    for (OrgUnit child : childrenByParentId.getOrDefault(parentId, List.of())) {
                        if (child.id() != null && visitedIds.add(child.id())) {
                            codes.add(child.code());
                            queue.addLast(child.id());
                        }
                    }
                }
            }
        }
        List<Person> result = new ArrayList<>();
        for (String code : codes) {
            result.addAll(personByOrgCode.getOrDefault(code, List.of()));
        }
        return List.copyOf(result);
    }

    /** 组织单元总数，供自检与测试断言。 */
    public int orgUnitCount() {
        return orgByCode.size();
    }

    private static final class Builder {

        private final String appCode;
        private final Map<String, List<DictItem>> dict = new LinkedHashMap<>();
        private final Map<String, String> param = new LinkedHashMap<>();
        private final Set<String> secretParams = new LinkedHashSet<>();
        private final Map<String, OrgUnit> orgByCode = new LinkedHashMap<>();
        private final Map<String, List<OrgUnit>> childrenByParentId = new LinkedHashMap<>();
        private final Map<String, Person> personById = new LinkedHashMap<>();
        private final Map<String, Person> personByEmployeeNo = new LinkedHashMap<>();
        private final Map<String, List<Person>> personByOrgCode = new LinkedHashMap<>();

        private Builder(String appCode) {
            this.appCode = appCode;
        }

        /** 先按 sort，再让本应用项排在全局项之前（与 HttpDictClient 语义一致）。 */
        private List<DictItem> sortDict(List<DictItem> items) {
            List<DictItem> sorted = new ArrayList<>(items);
            sorted.sort(Comparator.comparingInt(DictItem::sort)
                    .thenComparing(item -> appCode != null && appCode.equals(item.appCode()) ? 0 : 1));
            return List.copyOf(sorted);
        }

        private LocalFoundationData build() {
            return new LocalFoundationData(this);
        }
    }
}
