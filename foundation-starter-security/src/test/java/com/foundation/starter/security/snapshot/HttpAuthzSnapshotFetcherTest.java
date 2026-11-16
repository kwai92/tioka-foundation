package com.foundation.starter.security.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.core.contract.FoundationApiVersion;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** ADP-03：授权快照回源与其余 Foundation 出站通道一样必须声明契约主版本。 */
class HttpAuthzSnapshotFetcherTest {

    @Test
    void 快照回源携带契约版本头() {
        AtomicReference<Request> captured = new AtomicReference<>();
        OkHttpClient http = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    captured.set(chain.request());
                    throw new IOException("capture then stop");
                })
                .build();
        HttpAuthzSnapshotFetcher fetcher = new HttpAuthzSnapshotFetcher(
                http, new ObjectMapper(), "http://localhost:8080", null);

        assertThrows(IllegalStateException.class, () -> fetcher.version("tioka", "per_1"));
        assertEquals(FoundationApiVersion.CURRENT,
                captured.get().header(FoundationApiVersion.HEADER));
    }
}
