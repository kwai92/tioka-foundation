package com.foundation.file.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 文件领域规则（F-09 BAS-17 §关键类）：入参校验、MIME 规范化、对象键与分片数。 */
class FileRulesTest {

    @Test
    void requireAppCodeReturnsValueAndRejectsBlank() {
        assertThat(FileRules.requireAppCode("app_x")).isEqualTo("app_x");
        assertThatThrownBy(() -> FileRules.requireAppCode("  "))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> FileRules.requireAppCode(null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void requireUploadValidatesNameAndSize() {
        FileRules.requireUpload("a.txt", 0);

        assertThatThrownBy(() -> FileRules.requireUpload(" ", 1))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> FileRules.requireUpload("a".repeat(513), 1))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> FileRules.requireUpload("a.txt", -1))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void normalizeContentTypeDefaultsAndTruncates() {
        assertThat(FileRules.normalizeContentType(null)).isEqualTo("application/octet-stream");
        assertThat(FileRules.normalizeContentType("   ")).isEqualTo("application/octet-stream");
        assertThat(FileRules.normalizeContentType(" text/plain ")).isEqualTo("text/plain");
        assertThat(FileRules.normalizeContentType("x".repeat(200))).hasSize(128);
    }

    @Test
    void storageKeyPrefixesAppCode() {
        assertThat(FileRules.storageKey("app_x", "fil_1")).isEqualTo("app_x/fil_1");
    }

    @Test
    void partCountRoundsUpAndTreatsEmptyAsSinglePart() {
        assertThat(FileRules.partCount(0, 100)).isEqualTo(1);
        assertThat(FileRules.partCount(-5, 100)).isEqualTo(1);
        assertThat(FileRules.partCount(100, 100)).isEqualTo(1);
        assertThat(FileRules.partCount(101, 100)).isEqualTo(2);
        assertThat(FileRules.partCount(1000, 300)).isEqualTo(4);
    }

    @Test
    void partCountRejectsNonPositivePartSize() {
        assertThatThrownBy(() -> FileRules.partCount(10, 0))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL);
    }
}
