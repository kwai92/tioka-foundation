package com.foundation.authz.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 清单内容哈希（F-05 BAS-05 §测试 {@code ManifestHashTest}）。 */
class ManifestHasherTest {

    @Test
    void sameContentSameHashRegardlessOfOrder() {
        ManifestDocument first = new ManifestDocument(3,
                List.of(menu("a.menu", "/a"), menu("b.menu", "/b")),
                List.of(permission("contract:read"), permission("contract:write")));
        ManifestDocument reordered = new ManifestDocument(3,
                List.of(menu("b.menu", "/b"), menu("a.menu", "/a")),
                List.of(permission("contract:write"), permission("contract:read")));

        assertThat(ManifestHasher.contentHash(first)).isEqualTo(ManifestHasher.contentHash(reordered));
    }

    @Test
    void contentChangeChangesHash() {
        ManifestDocument baseline = new ManifestDocument(3, List.of(menu("a.menu", "/a")), List.of());
        ManifestDocument routeChanged = new ManifestDocument(3, List.of(menu("a.menu", "/a/v2")), List.of());
        ManifestDocument versionChanged = new ManifestDocument(4, List.of(menu("a.menu", "/a")), List.of());

        assertThat(ManifestHasher.contentHash(routeChanged)).isNotEqualTo(ManifestHasher.contentHash(baseline));
        assertThat(ManifestHasher.contentHash(versionChanged)).isNotEqualTo(ManifestHasher.contentHash(baseline));
    }

    @Test
    void hashIsLowercaseSha256() {
        String hash = ManifestHasher.contentHash(new ManifestDocument(1, List.of(), List.of()));

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    }

    private static ManifestMenu menu(String code, String routePath) {
        return new ManifestMenu(code, code, MenuType.MENU, null, routePath, null, null, 0, true, null);
    }

    private static ManifestPermission permission(String code) {
        return new ManifestPermission(code, code, RiskLevel.LOW);
    }
}
