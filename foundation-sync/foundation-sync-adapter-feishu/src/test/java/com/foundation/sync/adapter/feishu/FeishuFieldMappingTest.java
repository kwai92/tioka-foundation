package com.foundation.sync.adapter.feishu;

import com.foundation.sync.domain.FieldMapper;
import com.foundation.sync.domain.FieldMapping;
import com.foundation.sync.domain.LocalLockResolver;
import com.foundation.sync.spi.ExternalIdentityHint;
import com.foundation.sync.spi.ExternalRecord;
import com.foundation.sync.spi.MatchBy;
import com.foundation.sync.spi.Page;
import com.foundation.sync.spi.PageCursor;
import com.foundation.sync.spi.SyncCredentialsRef;
import com.foundation.sync.spi.SyncObjectType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 飞书适配器字段映射与身份关联单测（F-08 BAS-12）：不启动 Spring 容器。
 */
class FeishuFieldMappingTest {

    @Test
    void departmentCodePrefixIsApplied() {
        FieldMapper mapper = new FieldMapper(List.of(new DepartmentCodePrefixConverter("FIN-")));
        List<FieldMapping> mappings = List.of(new FieldMapping("m1", "src_1", SyncObjectType.ORG_UNIT,
                "code", "code", null, DepartmentCodePrefixConverter.NAME, true));

        Map<String, Object> mapped = mapper.map(SyncObjectType.ORG_UNIT, mappings, Map.of("code", "D01"));

        assertThat(mapped).containsEntry("code", "FIN-D01");
    }

    @Test
    void prefixIsIdempotent() {
        DepartmentCodePrefixConverter converter = new DepartmentCodePrefixConverter("FIN-");

        assertThat(converter.convert("FIN-D01")).isEqualTo("FIN-D01");
    }

    @Test
    void nullAndBlankUntouched() {
        DepartmentCodePrefixConverter converter = new DepartmentCodePrefixConverter("FIN-");

        assertThat(converter.convert(null)).isNull();
        assertThat(converter.convert("")).isEqualTo("");
    }

    @Test
    void lockedFieldIsNotOverwritten() {
        FieldMapper mapper = new FieldMapper(List.of(new DepartmentCodePrefixConverter("FIN-")));
        List<FieldMapping> mappings = List.of(
                new FieldMapping("m1", "src_1", SyncObjectType.ORG_UNIT, "code", "code",
                        null, DepartmentCodePrefixConverter.NAME, true),
                new FieldMapping("m2", "src_1", SyncObjectType.ORG_UNIT, "name", "name",
                        null, null, false));

        Map<String, Object> mapped = mapper.map(SyncObjectType.ORG_UNIT, mappings,
                Map.of("code", "D01", "name", "研发部"));
        assertThat(mapped).containsEntry("code", "FIN-D01").containsEntry("name", "研发部");

        Map<String, Object> filtered = new LocalLockResolver().filterFields(mapped, Set.of("code"));

        assertThat(filtered).containsOnlyKeys("name");
        assertThat(filtered).doesNotContainKey("code");
        assertThat(filtered).containsEntry("name", "研发部");
    }

    @Test
    void feishuUserCarriesOpenIdAndUnionId() {
        FeishuUser user = FeishuUser.of("ou_1", "on_1", "uid_1", "张三", "zhang@example.com",
                "13800000000", "E100", List.of("od_1"), true, Instant.parse("2026-01-01T00:00:00Z"));

        ExternalRecord record = user.toExternalRecord();

        assertThat(record.objectType()).isEqualTo(SyncObjectType.PERSON);
        assertThat(record.externalId()).isEqualTo("ou_1");
        assertThat(record.deleted()).isFalse();
        assertThat(record.updatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(record.fields())
                .containsEntry("open_id", "ou_1")
                .containsEntry("union_id", "on_1")
                .containsEntry("emp_no", "E100");
    }

    @Test
    void resolutionPrefersEmpNoAndNeverName() {
        FeishuSyncAdapter adapter = new FeishuSyncAdapter(new FeishuProperties(), new StubFeishuApiClient());

        ExternalRecord withEmpNo = new ExternalRecord(SyncObjectType.PERSON, "ou_1",
                Map.of("emp_no", "E100", "email", "zhang@example.com"), null, false, null);
        Optional<ExternalIdentityHint> empNo = adapter.resolveExternalIdentity(withEmpNo);
        assertThat(empNo).isPresent();
        assertThat(empNo.get().by()).isEqualTo(MatchBy.EMP_NO);
        assertThat(empNo.get().source()).isEqualTo("FEISHU");
        assertThat(empNo.get().matchValue()).isEqualTo("E100");

        ExternalRecord withEmail = new ExternalRecord(SyncObjectType.PERSON, "ou_2",
                Map.of("email", "li@example.com"), null, false, null);
        Optional<ExternalIdentityHint> email = adapter.resolveExternalIdentity(withEmail);
        assertThat(email).isPresent();
        assertThat(email.get().by()).isEqualTo(MatchBy.VERIFIED_EMAIL);

        ExternalRecord onlyName = new ExternalRecord(SyncObjectType.PERSON, "ou_3",
                Map.of("name", "李四"), null, false, null);
        assertThat(adapter.resolveExternalIdentity(onlyName)).isEmpty();
    }

    @Test
    void departmentMappingCarriesParentAndLeader() {
        FeishuDepartment department = FeishuDepartment.of("od_2", "od_1", "研发部", 3, List.of("ou_1", "ou_2"));

        ExternalRecord record = department.toExternalRecord();

        assertThat(record.objectType()).isEqualTo(SyncObjectType.ORG_UNIT);
        assertThat(record.externalId()).isEqualTo("od_2");
        assertThat(record.parentExternalId()).isEqualTo("od_1");
        assertThat(record.deleted()).isFalse();
        assertThat(record.fields())
                .containsEntry("department_id", "od_2")
                .containsEntry("parent_id", "od_1")
                .containsEntry("name", "研发部")
                .containsEntry("order", 3)
                .containsEntry("leader_person_id", "ou_1");
    }

    @Test
    void eventVerifierAcceptsOwnSignature() {
        String timestamp = "1700000000";
        String nonce = "n-1";
        String encryptKey = "enc-key";
        String body = "{\"header\":{}}";
        String signature = FeishuEventVerifier.sign(timestamp, nonce, encryptKey, body);

        FeishuEventVerifier verifier = new FeishuEventVerifier(encryptKey);

        assertThat(verifier.verify(timestamp, nonce, body, signature)).isTrue();
        assertThat(verifier.verify(timestamp, nonce, body, signature.toUpperCase())).isTrue();
        assertThat(verifier.verify(timestamp, nonce, body, "deadbeef")).isFalse();
        assertThat(verifier.verify(timestamp, nonce, null, signature)).isFalse();
    }

    @Test
    void parseEventReadsTypeAndExternalId() {
        String json = "{\"header\":{\"event_type\":\"contact.department.deleted_v3\"},"
                + "\"event\":{\"object\":{\"open_department_id\":\"od_9\"}}}";

        FeishuEvent event = FeishuEvent.parse(json).orElseThrow();

        assertThat(event.objectType()).isEqualTo(SyncObjectType.ORG_UNIT);
        assertThat(event.deleted()).isTrue();
        assertThat(event.externalIds()).containsExactly("od_9");

        assertThat(FeishuEvent.parse("{not json")).isEmpty();
    }

    /** 内存桩出网客户端：不发起任何网络调用。 */
    private static final class StubFeishuApiClient implements FeishuApiClient {

        @Override
        public void authenticate(SyncCredentialsRef credentials) {
            // no-op
        }

        @Override
        public boolean ping() {
            return false;
        }

        @Override
        public Page<FeishuDepartment> departments(PageCursor cursor) {
            return Page.empty();
        }

        @Override
        public Page<FeishuUser> users(PageCursor cursor) {
            return Page.empty();
        }

        @Override
        public Optional<FeishuUser> user(String openId) {
            return Optional.empty();
        }

        @Override
        public Optional<FeishuDepartment> department(String openDepartmentId) {
            return Optional.empty();
        }
    }
}
