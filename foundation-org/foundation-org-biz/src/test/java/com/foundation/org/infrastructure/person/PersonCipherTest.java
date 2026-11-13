package com.foundation.org.infrastructure.person;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-03 BAS-02 §2.1：手机 / 邮箱 AES-256-GCM 加密与 SHA-256 查重哈希。 */
class PersonCipherTest {

    private final PersonCipher cipher = new PersonCipher("unit-test-master-key");

    @Test
    void encryptDecryptRoundTrip() {
        String ciphertext = cipher.encrypt("13800138000");

        assertThat(ciphertext).startsWith("v1:").isNotEqualTo("13800138000");
        assertThat(cipher.decrypt(ciphertext)).isEqualTo("13800138000");
    }

    @Test
    void samePlaintextProducesDifferentCiphertexts() {
        assertThat(cipher.encrypt("13800138000")).isNotEqualTo(cipher.encrypt("13800138000"));
    }

    @Test
    void blankInputYieldsNull() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.encrypt("  ")).isNull();
        assertThat(cipher.decrypt(null)).isNull();
        assertThat(cipher.hash(null)).isNull();
        assertThat(cipher.hash("  ")).isNull();
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String ciphertext = cipher.encrypt("13800138000");
        byte[] envelope = Base64.getDecoder().decode(ciphertext.substring("v1:".length()));
        envelope[envelope.length - 1] ^= 0x01;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(envelope);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void foreignKeyCannotDecrypt() {
        String ciphertext = cipher.encrypt("13800138000");
        PersonCipher other = new PersonCipher("another-master-key");

        assertThatThrownBy(() -> other.decrypt(ciphertext)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnknownEnvelope() {
        assertThatThrownBy(() -> cipher.decrypt("plain-text")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hashIsStableLowerHexAndCollidesForSameInput() {
        assertThat(cipher.hash("13800138000")).isEqualTo(cipher.hash("13800138000"));
        assertThat(cipher.hash("13800138000")).isNotEqualTo(cipher.hash("13800138001"));
        assertThat(cipher.hash("13800138000")).hasSize(64).matches("[0-9a-f]{64}");
        // 稳定性：SHA-256("abc") 的标准十六进制小写值
        assertThat(cipher.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void devMasterKeyFallbackWorks() {
        PersonCipher dev = new PersonCipher(null);

        assertThat(dev.decrypt(dev.encrypt("x"))).isEqualTo("x");
    }
}
