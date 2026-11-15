package com.foundation.starter.web.filter;

import com.foundation.starter.web.config.FoundationWebProperties;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 敏感数据脱敏（F-02 BAS-24 §foundation-starter-web）：query 与 header 命中键替换为 ***。 */
class SensitiveDataMaskerTest {

    private static final List<String> KEYS = new FoundationWebProperties().getSensitiveKeys();

    @Test
    void maskQueryStringMasksSensitiveValues() {
        String masked = SensitiveDataMasker.maskQueryString(
                "name=alice&password=secret&client_secret=abc&page=1", KEYS);

        assertThat(masked).isEqualTo("name=alice&password=***&client_secret=***&page=1");
    }

    @Test
    void maskQueryStringKeepsNullAndEmpty() {
        assertThat(SensitiveDataMasker.maskQueryString(null, KEYS)).isNull();
        assertThat(SensitiveDataMasker.maskQueryString("", KEYS)).isEmpty();
    }

    @Test
    void maskHeadersMasksSensitiveHeaders() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Authorization", List.of("Bearer abc"));
        headers.put("X-Api-Key", List.of("xyz"));
        headers.put("Accept", List.of("application/json"));
        headers.put(null, List.of("ignored"));

        Map<String, String> masked = SensitiveDataMasker.maskHeaders(headers, KEYS);

        assertThat(masked).containsEntry("Authorization", "***");
        assertThat(masked).containsEntry("X-Api-Key", "***");
        assertThat(masked).containsEntry("Accept", "application/json");
        assertThat(masked).doesNotContainKey(null);
    }

    @Test
    void maskHeadersHandlesNullInput() {
        assertThat(SensitiveDataMasker.maskHeaders(null, KEYS)).isEmpty();
    }
}
