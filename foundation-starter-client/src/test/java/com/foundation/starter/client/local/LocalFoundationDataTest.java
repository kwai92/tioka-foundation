package com.foundation.starter.client.local;

import com.foundation.starter.client.config.FoundationClientProperties;
import com.foundation.starter.client.dict.DictItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ADP-08：本地种子视图的转换、排序、敏感参数过滤与组织树展开。 */
class LocalFoundationDataTest {

    private final LocalFoundationData data = LocalFoundationData.from(seed(), "tioka");

    @Test
    void 字典项按sort排序且本应用项优先_不存在的类型返回空列表() {
        List<DictItem> items = data.dictItems("gender");
        assertEquals(3, items.size());
        assertEquals("X1", items.get(0).itemCode());
        assertEquals("G1", items.get(1).itemCode());
        assertEquals("M", items.get(2).itemCode());
        assertTrue(data.dictItems("missing").isEmpty());
    }

    @Test
    void 参数敏感键与未配置键一律返回空() {
        assertEquals(Optional.of("20"), data.paramValue("pageSize"));
        assertTrue(data.paramValue("dbPassword").isEmpty());
        assertTrue(data.paramValue("missing").isEmpty());
        assertTrue(data.paramValue(null).isEmpty());
    }

    @Test
    void 组织按编码查询与直接下级排序() {
        assertEquals("总部", data.orgByCode("root").orElseThrow().name());
        assertTrue(data.orgByCode("missing").isEmpty());
        assertEquals(List.of("dept-a", "dept-b"),
                data.children("root").stream().map(unit -> unit.code()).toList());
        assertEquals(1, data.children("dept-a").size());
        assertTrue(data.children("missing").isEmpty());
        assertEquals(4, data.orgUnitCount());
    }

    @Test
    void 人员按ID工号查询_含下级组织时递归展开整棵子树() {
        assertEquals("张三", data.personById("per_1").orElseThrow().name());
        assertEquals("per_2", data.personByEmployeeNo("E002").orElseThrow().id());
        assertTrue(data.personById("missing").isEmpty());
        assertEquals(1, data.personsInOrg("root", false).size());
        assertEquals(3, data.personsInOrg("root", true).size());
        assertEquals(2, data.personsInOrg("dept-a", true).size());
        assertTrue(data.personsInOrg("missing", true).isEmpty());
    }

    @Test
    void 空配置构建出的视图全部返回空结果而不抛异常() {
        LocalFoundationData empty = LocalFoundationData.from(
                new FoundationClientProperties.Local(), null);
        assertTrue(empty.dictItems("any").isEmpty());
        assertTrue(empty.paramValue("any").isEmpty());
        assertTrue(empty.orgByCode("any").isEmpty());
        assertTrue(empty.children("any").isEmpty());
        assertTrue(empty.personById("any").isEmpty());
        assertTrue(empty.personsInOrg("any", true).isEmpty());
        assertEquals(0, empty.orgUnitCount());
    }

    private static FoundationClientProperties.Local seed() {
        FoundationClientProperties.Local local = new FoundationClientProperties.Local();
        local.setDict(Map.of("gender", List.of(
                dict("M", "男", 3, null),
                dict("G1", "全局项", 1, null),
                dict("X1", "本应用项", 1, "tioka"))));
        local.setParam(Map.of("pageSize", "20", "dbPassword", "plaintext"));
        local.setSecretParams(List.of("dbPassword"));
        local.setOrgUnits(List.of(
                org("org-root", "root", "总部", null, 0),
                org("org-a", "dept-a", "部门A", "org-root", 1),
                org("org-b", "dept-b", "部门B", "org-root", 2),
                org("org-a1", "dept-a1", "科室A1", "org-a", 1)));
        local.setPersons(List.of(
                person("per_1", "E001", "张三", "root"),
                person("per_2", "E002", "李四", "dept-a"),
                person("per_3", "E003", "王五", "dept-a1")));
        return local;
    }

    private static FoundationClientProperties.DictSeed dict(String code, String name,
                                                             int sort, String appCode) {
        FoundationClientProperties.DictSeed seed = new FoundationClientProperties.DictSeed();
        seed.setItemCode(code);
        seed.setItemName(name);
        seed.setSort(sort);
        seed.setAppCode(appCode);
        return seed;
    }

    private static FoundationClientProperties.OrgUnitSeed org(String id, String code, String name,
                                                               String parentId, int sort) {
        FoundationClientProperties.OrgUnitSeed seed = new FoundationClientProperties.OrgUnitSeed();
        seed.setId(id);
        seed.setCode(code);
        seed.setName(name);
        seed.setParentId(parentId);
        seed.setSort(sort);
        return seed;
    }

    private static FoundationClientProperties.PersonSeed person(String id, String employeeNo,
                                                                 String name, String orgCode) {
        FoundationClientProperties.PersonSeed seed = new FoundationClientProperties.PersonSeed();
        seed.setId(id);
        seed.setEmployeeNo(employeeNo);
        seed.setName(name);
        seed.setPrimaryOrgUnit(orgCode);
        return seed;
    }
}
