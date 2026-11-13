package com.foundation.common.param.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-02 BAS-09：敏感参数 AES-256-GCM 信封加密。 */
class AesGcmParamCipherTest {

    private final ParamCipher cipher = new AesGcmParamCipher("unit-test-master-key");

    @Test
    void encryptDecryptRoundTrip() {
        String ciphertext = cipher.encrypt("p@ssw0rd-中文");

        assertThat(ciphertext).startsWith("v1:").isNotEqualTo("p@ssw0rd-中文");
        assertThat(cipher.decrypt(ciphertext)).isEqualTo("p@ssw0rd-中文");
    }

    @Test
    void samePlaintextProducesDifferentCiphertexts() {
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String ciphertext = cipher.encrypt("p@ssw0rd");
        byte[] envelope = Base64.getDecoder().decode(ciphertext.substring("v1:".length()));
        envelope[envelope.length - 1] ^= 0x01;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(envelope);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void foreignKeyCannotDecrypt() {
        String ciphertext = cipher.encrypt("p@ssw0rd");
        ParamCipher other = new AesGcmParamCipher("another-master-key");

        assertThatThrownBy(() -> other.decrypt(ciphertext)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnknownEnvelope() {
        assertThatThrownBy(() -> cipher.decrypt("plain-text"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
