package com.foundation.sync.spi;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** F-08 BAS-11 §2、§3：来源 SPI 契约（能力矩阵、探测结果、对象类型、记录载体）。 */
class SyncSourceSpiContractTest {

    @Test
    void noneCapabilitiesDeclareNothing() {
        SourceCapabilities capabilities = SourceCapabilities.none();

        assertThat(capabilities.objects()).isEmpty();
        assertThat(capabilities.incrementalModes()).isEmpty();
        assertThat(capabilities.providesExternalIdentity()).isFalse();
        assertThat(capabilities.supportsFieldConversion()).isFalse();
        assertThat(capabilities.supports(SyncObjectType.PERSON)).isFalse();
        assertThat(capabilities.supportsIncremental()).isFalse();
    }

    @Test
    void supportsOnlyDeclaredObjects() {
        SourceCapabilities capabilities = new SourceCapabilities(
                EnumSet.of(SyncObjectType.ORG_UNIT, SyncObjectType.PERSON),
                EnumSet.of(IncrementalMode.TIMESTAMP), true, true);

        assertThat(capabilities.supports(SyncObjectType.ORG_UNIT)).isTrue();
        assertThat(capabilities.supports(SyncObjectType.PERSON)).isTrue();
        assertThat(capabilities.supports(SyncObjectType.POSITION)).isFalse();
        assertThat(capabilities.providesExternalIdentity()).isTrue();
        assertThat(capabilities.supportsFieldConversion()).isTrue();
    }

    @Test
    void fullOnlyModeIsNotIncremental() {
        SourceCapabilities capabilities = new SourceCapabilities(
                EnumSet.of(SyncObjectType.PERSON), EnumSet.of(IncrementalMode.FULL_ONLY), false, false);

        assertThat(capabilities.supportsIncremental()).isFalse();
    }

    @Test
    void anyRealIncrementalModeCountsAsIncremental() {
        assertThat(IncrementalMode.TIMESTAMP.supportsIncremental()).isTrue();
        assertThat(IncrementalMode.CHANGE_NUMBER.supportsIncremental()).isTrue();
        assertThat(IncrementalMode.EVENT_CALLBACK.supportsIncremental()).isTrue();
        assertThat(IncrementalMode.FULL_ONLY.supportsIncremental()).isFalse();

        SourceCapabilities capabilities = new SourceCapabilities(
                EnumSet.of(SyncObjectType.PERSON),
                EnumSet.of(IncrementalMode.FULL_ONLY, IncrementalMode.EVENT_CALLBACK), false, false);
        assertThat(capabilities.supportsIncremental()).isTrue();
    }

    @Test
    void probeOkAllOkAndNoFailures() {
        ProbeResult result = ProbeResult.ok(java.util.List.of(
                ProbeResult.ProbeItem.pass("auth"), ProbeResult.ProbeItem.pass("fetch-org-unit")));

        assertThat(result.reachable()).isTrue();
        assertThat(result.allOk()).isTrue();
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void probeUnreachableIsNotAllOkAndListsFailures() {
        ProbeResult result = ProbeResult.failed(java.util.List.of(
                ProbeResult.ProbeItem.pass("auth"),
                ProbeResult.ProbeItem.fail("fetch-person", "403")));

        assertThat(result.reachable()).isFalse();
        assertThat(result.allOk()).isFalse();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).message()).isEqualTo("403");
    }

    @Test
    void reachableButFailingItemIsNotAllOk() {
        ProbeResult result = ProbeResult.ok(java.util.List.of(
                ProbeResult.ProbeItem.fail("fetch-person", "403")));

        assertThat(result.reachable()).isTrue();
        assertThat(result.allOk()).isFalse();
    }

    @Test
    void externalIdentityIsNotMasterData() {
        assertThat(SyncObjectType.EXTERNAL_IDENTITY.isMasterData()).isFalse();
        assertThat(SyncObjectType.PERSON.isMasterData()).isTrue();
        assertThat(SyncObjectType.ORG_UNIT.isMasterData()).isTrue();
        assertThat(SyncObjectType.POSITION.isMasterData()).isTrue();
    }

    @Test
    void objectTypeParsingIsLenientAndCaseInsensitive() {
        assertThat(SyncObjectType.of(" person ")).isEqualTo(SyncObjectType.PERSON);
        assertThat(SyncObjectType.of("org_unit")).isEqualTo(SyncObjectType.ORG_UNIT);
        assertThat(SyncObjectType.of("UNKNOWN")).isNull();
        assertThat(SyncObjectType.of(null)).isNull();
    }

    @Test
    void externalRecordHelpersKeepInvariants() {
        ExternalRecord record = ExternalRecord.of(SyncObjectType.PERSON, "u1", Map.of("name", "张三"));

        assertThat(record.deleted()).isFalse();
        assertThat(record.parentExternalId()).isNull();
        assertThat(record.updatedAt()).isNull();
        assertThat(record.key("FEISHU")).isEqualTo(SyncKey.of("FEISHU", "u1"));

        ExternalRecord mapped = record.withFields(Map.of("name", "李四"));
        assertThat(mapped.externalId()).isEqualTo("u1");
        assertThat(mapped.fields()).containsEntry("name", "李四");

        assertThat(record.fieldsMutable()).containsEntry("name", "张三");
    }

    @Test
    void credentialsRefOnlyCarriesReference() {
        SyncCredentialsRef ref = new SyncCredentialsRef("FEISHU", "secret://foundation/feishu-app-secret");

        assertThat(ref.sourceType()).isEqualTo("FEISHU");
        assertThat(ref.secretRef()).isEqualTo("secret://foundation/feishu-app-secret");
    }

    @Test
    void adapterFetchOneIsOptionalByContract() {
        SyncSourceAdapter adapter = new SyncSourceAdapter() {
            @Override
            public String sourceType() {
                return "STUB";
            }

            @Override
            public SourceCapabilities capabilities() {
                return SourceCapabilities.none();
            }

            @Override
            public void authenticate(SyncCredentialsRef credentials) {
            }

            @Override
            public ProbeResult probe() {
                return ProbeResult.ok(java.util.List.of());
            }

            @Override
            public Page<ExternalRecord> fetchFull(SyncObjectType objectType, PageCursor cursor) {
                return Page.empty();
            }

            @Override
            public Page<ExternalRecord> fetchIncremental(SyncObjectType objectType, IncrementalCursor cursor) {
                return Page.empty();
            }

            @Override
            public Optional<ExternalRecord> fetchOne(SyncObjectType objectType, String externalId) {
                return Optional.empty();
            }

            @Override
            public IncrementalCursor advanceCursor(IncrementalCursor cursor, Page<ExternalRecord> page) {
                return cursor;
            }

            @Override
            public Optional<ExternalIdentityHint> resolveExternalIdentity(ExternalRecord record) {
                return Optional.empty();
            }
        };

        assertThat(adapter.sourceType()).isEqualTo("STUB");
        assertThat(adapter.capabilities().supportsIncremental()).isFalse();
        assertThat(adapter.fetchOne(SyncObjectType.PERSON, "u1")).isEmpty();
        assertThat(adapter.fetchFull(SyncObjectType.PERSON, PageCursor.first()).items()).isEmpty();

        Set<SyncObjectType> declared = adapter.capabilities().objects();
        assertThat(declared).isEmpty();
    }
}
