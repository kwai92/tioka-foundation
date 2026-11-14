package com.foundation.file.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 内容摘要（F-09 BAS-17）：字节 / 流式口径一致，16 进制与定长比较。 */
class Sha256Test {

    private static final String ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private static final String EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void hashesBytesAsLowerCaseHex() {
        assertThat(Sha256.hex("abc".getBytes(StandardCharsets.UTF_8))).isEqualTo(ABC);
    }

    @Test
    void nullContentHashesAsEmptySequence() {
        assertThat(Sha256.hex((byte[]) null)).isEqualTo(EMPTY);
    }

    @Test
    void streamingHashMatchesByteHash() {
        byte[] content = "分片合并后的内容".getBytes(StandardCharsets.UTF_8);
        assertThat(Sha256.hex(new ByteArrayInputStream(content))).isEqualTo(Sha256.hex(content));
        assertThat(Sha256.hex(new ByteArrayInputStream(new byte[0]))).isEqualTo(EMPTY);
    }

    @Test
    void requireHexAcceptsValidDigest() {
        assertThat(Sha256.requireHex(ABC)).isEqualTo(ABC);
    }

    @Test
    void requireHexRejectsWrongLength() {
        assertThatThrownBy(() -> Sha256.requireHex("abc"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> Sha256.requireHex(null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void requireHexRejectsNonHex() {
        assertThatThrownBy(() -> Sha256.requireHex("z".repeat(64)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void constantTimeEqualsComparesContent() {
        assertThat(Sha256.constantTimeEquals(ABC, ABC)).isTrue();
        assertThat(Sha256.constantTimeEquals(ABC, EMPTY)).isFalse();
        assertThat(Sha256.constantTimeEquals(null, ABC)).isFalse();
        assertThat(Sha256.constantTimeEquals(ABC, null)).isFalse();
    }
}
