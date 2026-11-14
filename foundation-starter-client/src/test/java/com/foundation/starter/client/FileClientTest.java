package com.foundation.starter.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.client.file.FileClient;
import com.foundation.starter.client.file.FileMetadata;
import com.foundation.starter.client.file.FileUploadResult;
import com.foundation.starter.client.file.HttpFileClient;
import com.foundation.starter.client.file.SignedDownloadUrl;
import com.foundation.starter.client.http.CircuitBreaker;
import com.foundation.starter.client.http.FoundationHttpClient;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 文件客户端（F-09 BAS-17 §业务集成方式一「SDK / REST」）：上传 / 元数据 / 下载 / 预签名 / 删除。 */
class FileClientTest {

    private MockWebServer server;
    private FileClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        FoundationHttpClient http = new FoundationHttpClient(new OkHttpClient(), new ObjectMapper(),
                baseUrl(), null, new CircuitBreaker(50, 20, 60_000, 30_000), 1, 1);
        client = new HttpFileClient(http);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void uploadPostsMultipartAndParsesResult() throws Exception {
        server.enqueue(json("{\"fileId\":\"fil_1\",\"sha256\":\"abc\",\"size\":3}"));

        FileUploadResult result = client.upload("a.txt", "text/plain", "abc".getBytes(StandardCharsets.UTF_8),
                "SECRET");

        assertThat(result.fileId()).isEqualTo("fil_1");
        assertThat(result.sha256()).isEqualTo("abc");
        assertThat(result.size()).isEqualTo(3);
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/fdn/files");
        assertThat(request.getHeader("Content-Type")).startsWith("multipart/form-data");
        assertThat(request.getBody().readUtf8())
                .contains("name=\"file\"").contains("a.txt").contains("SECRET");
    }

    @Test
    void metadataGetsAndParsesFields() throws Exception {
        server.enqueue(json("{\"id\":\"fil_1\",\"appCode\":\"app_x\",\"fileName\":\"a.txt\","
                + "\"contentType\":\"text/plain\",\"sizeBytes\":3,\"sha256\":\"abc\","
                + "\"storageKind\":\"LOCAL\",\"dataLabel\":\"INTERNAL\",\"scanStatus\":\"SKIPPED\","
                + "\"status\":\"ACTIVE\",\"uploadedBy\":\"u1\",\"createAt\":\"2026-11-14T00:00:00Z\"}"));

        FileMetadata metadata = client.metadata("fil_1");

        assertThat(metadata.id()).isEqualTo("fil_1");
        assertThat(metadata.appCode()).isEqualTo("app_x");
        assertThat(metadata.sizeBytes()).isEqualTo(3);
        assertThat(metadata.scanStatus()).isEqualTo("SKIPPED");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/fdn/files/fil_1");
    }

    @Test
    void downloadReturnsRawBytes() throws Exception {
        server.enqueue(new MockResponse().setBody("hello"));

        assertThat(client.download("fil_1")).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/fdn/files/fil_1/content");
    }

    @Test
    void signedUrlPassesDispositionAndParsesUrl() throws Exception {
        server.enqueue(json("{\"url\":\"http://x/signed/t\",\"method\":\"GET\","
                + "\"expiresAt\":\"2026-11-14T00:10:00Z\"}"));

        SignedDownloadUrl signed = client.signedUrl("fil_1", "attachment");

        assertThat(signed.url()).isEqualTo("http://x/signed/t");
        assertThat(signed.method()).isEqualTo("GET");
        assertThat(server.takeRequest().getPath())
                .isEqualTo("/api/fdn/files/fil_1/signed-url?disposition=attachment");
    }

    @Test
    void deleteIssuesDeleteRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        client.delete("fil_1");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).isEqualTo("/api/fdn/files/fil_1");
    }

    private String baseUrl() {
        String url = server.url("/").toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }
}
