package com.foundation.file.storage;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 短时下载令牌（F-09 BAS-17）：签名、过期与篡改一律拒绝。 */
class UrlSignerTest {

    private static final Instant NOW = Instant.parse("2026-11-14T00:00:00Z");

    private final UrlSigner signer = new UrlSigner("unit-test-secret");

    @Test
    void signedTokenVerifiesToOriginalReference() {
        String token = signer.sign("app_foundation", "fil_1", NOW.plus(Duration.ofMinutes(10)));

        UrlSigner.SignedRef ref = signer.verify(token, NOW).orElseThrow();

        assertThat(ref.appCode()).isEqualTo("app_foundation");
        assertThat(ref.fileId()).isEqualTo("fil_1");
        assertThat(ref.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void expiredTokenIsRejected() {
        String token = signer.sign("app_foundation", "fil_1", NOW.minusSeconds(1));

        assertThat(signer.verify(token, NOW)).isEmpty();
    }

    @Test
    void tokenWithSwappedSignatureIsRejected() {
        String first = signer.sign("app_foundation", "fil_1", NOW.plusSeconds(60));
        String second = signer.sign("app_foundation", "fil_2", NOW.plusSeconds(60));
        String tampered = first.substring(0, first.indexOf('.')) + second.substring(second.indexOf('.'));

        assertThat(signer.verify(tampered, NOW)).isEmpty();
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        String token = new UrlSigner("other-secret").sign("app_foundation", "fil_1", NOW.plusSeconds(60));

        assertThat(signer.verify(token, NOW)).isEmpty();
    }

    @Test
    void malformedTokenIsRejected() {
        assertThat(signer.verify(null, NOW)).isEmpty();
        assertThat(signer.verify("", NOW)).isEmpty();
        assertThat(signer.verify("not-a-token", NOW)).isEmpty();
        assertThat(signer.verify(".", NOW)).isEmpty();
    }
}
