package com.foundation.sync.adapter.ldap;

import com.foundation.sync.spi.ExternalRecord;
import com.foundation.sync.spi.SyncObjectType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LDAP 适配器纯函数单测（F-08 BAS-12）：覆盖 objectGUID / DN 归一与映射，不启动 Spring 容器。
 */
class LdapObjectGuidTest {

    private static final String DN = "OU=研发部,OU=技术中心,DC=example,DC=com";

    @Test
    void objectGuidIsNormalizedToHex() {
        byte[] guid = {0x01, 0x23, (byte) 0xab, (byte) 0xff,
                0x00, 0x10, 0x20, 0x30,
                0x40, 0x50, 0x60, 0x70,
                (byte) 0x80, (byte) 0x90, (byte) 0xa0, (byte) 0xb0};

        String hex = ObjectGuidCodec.hex(guid);

        assertThat(hex).isEqualTo("0123abff00102030405060708090a0b0");
        assertThat(hex).hasSize(32);
        assertThat(ObjectGuidCodec.normalize(guid)).isEqualTo(hex);
    }

    @Test
    void objectGuidHexHandlesNullAndEmpty() {
        assertThat(ObjectGuidCodec.hex(null)).isNull();
        assertThat(ObjectGuidCodec.hex(new byte[0])).isEmpty();
    }

    @Test
    void objectGuidProducesDashedUuid() {
        byte[] guid = {0x01, 0x23, (byte) 0xab, (byte) 0xff,
                0x00, 0x10, 0x20, 0x30,
                0x40, 0x50, 0x60, 0x70,
                (byte) 0x80, (byte) 0x90, (byte) 0xa0, (byte) 0xb0};

        String uuid = ObjectGuidCodec.dashedUuid(guid);

        assertThat(uuid).isEqualTo("FFAB2301-1000-3020-4050-60708090A0B0");
        assertThat(uuid).hasSize(36);
        assertThat(uuid).matches("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}");
        assertThat(uuid.chars().filter(c -> c == '-')).hasSize(4);
        assertThat(uuid).isEqualTo(uuid.toUpperCase());
        assertThat(ObjectGuidCodec.dashedUuid(new byte[15])).isNull();
        assertThat(ObjectGuidCodec.dashedUuid(null)).isNull();
    }

    @Test
    void entryUuidIsLowered() {
        assertThat(ObjectGuidCodec.entryUuid("A1B2")).isEqualTo("a1b2");
        assertThat(ObjectGuidCodec.entryUuid("  A1B2  ")).isEqualTo("a1b2");
        assertThat(ObjectGuidCodec.entryUuid(null)).isNull();
        assertThat(ObjectGuidCodec.entryUuid("   ")).isNull();
    }

    @Test
    void dnToOrgPathIsCorrect() {
        assertThat(LdapDn.orgPath(DN)).isEqualTo("com/example/技术中心/研发部");
    }

    @Test
    void dnDepthCountsOuAndDc() {
        assertThat(LdapDn.depth(DN)).isEqualTo(4);
        assertThat(LdapDn.components(DN)).containsExactly("com", "example", "技术中心", "研发部");
    }

    @Test
    void parentDnDropsLeafRdn() {
        assertThat(LdapDn.parentDn(DN)).isEqualTo("OU=技术中心,DC=example,DC=com");
        assertThat(LdapDn.parentDn("DC=com")).isEmpty();
    }

    @Test
    void sameNameOuDistinguishedByFullPath() {
        String a = "OU=研发部,OU=技术中心,DC=example,DC=com";
        String b = "OU=研发部,OU=产品部,DC=example,DC=com";

        assertThat(LdapDn.orgPath(a)).isEqualTo("com/example/技术中心/研发部");
        assertThat(LdapDn.orgPath(b)).isEqualTo("com/example/产品部/研发部");
        assertThat(LdapDn.orgPath(a)).isNotEqualTo(LdapDn.orgPath(b));
    }

    @Test
    void withinDepthEnforcesMaxTreeDepth() {
        assertThat(LdapDn.withinDepth(DN, 4)).isTrue();
        assertThat(LdapDn.withinDepth(DN, 3)).isFalse();
    }

    @Test
    void disabledAccountMapsToDeletedPerson() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("cn", "张三");
        attributes.put("mail", "zhangsan@example.com");

        LdapEntry entry = LdapEntry.of("CN=张三," + DN, attributes, "0123abff00102030405060708090a0b0",
                "100", Instant.parse("2026-01-01T00:00:00Z"), true);

        ExternalRecord record = entry.toPersonRecord();

        assertThat(record.objectType()).isEqualTo(SyncObjectType.PERSON);
        assertThat(record.deleted()).isTrue();
        assertThat(record.fields()).containsEntry("name", "张三").containsEntry("email", "zhangsan@example.com");
    }
}
