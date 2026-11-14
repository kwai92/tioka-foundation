package com.foundation.file.storage;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.file.api.UploadedPart;
import com.foundation.file.domain.StorageKind;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S3 兼容对象存储后端（ADR-026）：MinIO / 客户 OSS。
 *
 * <p>不引入 AWS SDK：出站调用复用 {@code okhttp}，签名按 {@link AwsSignatureV4} 自实现，
 * 从而「预签名直传 / 直读」两条路径都不需要把长期凭证发给业务应用。
 *
 * <p>分片上传：{@code initMultipart} 取 {@code UploadId}，分片地址由 {@code presignedUploadPart}
 * 直连对象存储，{@code completeMultipart} 在服务端收口。
 */
public class S3StorageAdapter implements ObjectStorageAdapter {

    private static final Pattern UPLOAD_ID = Pattern.compile("<UploadId>([^<]+)</UploadId>");
    private static final String XML_ERROR = "<Error>";

    private final StorageSettings settings;
    private final OkHttpClient http;
    private final String host;

    public S3StorageAdapter(StorageSettings settings) {
        this(settings, defaultClient());
    }

    public S3StorageAdapter(StorageSettings settings, OkHttpClient http) {
        this.settings = settings;
        this.http = http;
        if (settings.s3Endpoint() == null || settings.s3Endpoint().isBlank()) {
            throw new BizException(ErrorCode.INTERNAL, "foundation.file.storage.s3.endpoint 未配置");
        }
        if (settings.s3Bucket() == null || settings.s3Bucket().isBlank()) {
            throw new BizException(ErrorCode.INTERNAL, "foundation.file.storage.s3.bucket 未配置");
        }
        HttpUrl base = HttpUrl.get(settings.s3Endpoint());
        int port = base.port();
        boolean defaultPort = (base.isHttps() && port == 443) || (!base.isHttps() && port == 80);
        this.host = defaultPort ? base.host() : base.host() + ":" + port;
    }

    @Override
    public StorageKind kind() {
        return StorageKind.S3;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        byte[] payload = content == null ? new byte[0] : content;
        Request.Builder builder = authorized("PUT", key, Map.of(), payload);
        builder.put(RequestBody.create(payload,
                MediaType.parse(contentType == null ? "application/octet-stream" : contentType)));
        execute(builder, "写入对象失败");
    }

    @Override
    public InputStream openStream(String key) {
        Request request = authorized("GET", key, Map.of(), new byte[0]).build();
        try {
            Response response = http.newCall(request).execute();
            if (!response.isSuccessful()) {
                response.close();
                throw new BizException(ErrorCode.NOT_FOUND, "对象不存在：" + key);
            }
            ResponseBody body = response.body();
            InputStream raw = body == null ? InputStream.nullInputStream() : body.byteStream();
            return new FilterInputStream(raw) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        response.close();
                    }
                }
            };
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL, "读取对象失败：" + ex.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        execute(authorized("DELETE", key, Map.of(), new byte[0]), "删除对象失败");
    }

    @Override
    public String initMultipart(String key, String contentType) {
        String xml = execute(authorized("POST", key, Map.of("uploads", ""), new byte[0]), "发起分片上传失败");
        Matcher matcher = UPLOAD_ID.matcher(xml == null ? "" : xml);
        if (!matcher.find()) {
            throw new BizException(ErrorCode.INTERNAL, "对象存储未返回 UploadId");
        }
        return matcher.group(1);
    }

    @Override
    public Optional<String> presignedUploadPart(String key, String nativeUploadId, int partNumber, Duration ttl) {
        if (nativeUploadId == null || nativeUploadId.isBlank()) {
            return Optional.empty();
        }
        Map<String, String> query = new LinkedHashMap<>();
        query.put("partNumber", String.valueOf(partNumber));
        query.put("uploadId", nativeUploadId);
        return Optional.of(presignedUrl("PUT", key, query, ttl));
    }

    @Override
    public void completeMultipart(String key, String nativeUploadId, List<UploadedPart> parts) {
        StringBuilder xml = new StringBuilder("<CompleteMultipartUpload>");
        for (UploadedPart part : parts) {
            xml.append("<Part><PartNumber>").append(part.partNumber()).append("</PartNumber><ETag>")
                    .append(quotedEtag(part.etag())).append("</ETag></Part>");
        }
        xml.append("</CompleteMultipartUpload>");
        byte[] body = xml.toString().getBytes(StandardCharsets.UTF_8);
        String response = execute(authorized("POST", key, Map.of("uploadId", nativeUploadId), body),
                "合并分片失败");
        if (response != null && response.contains(XML_ERROR)) {
            throw new BizException(ErrorCode.INTERNAL, "对象存储合并分片失败");
        }
    }

    @Override
    public void abortMultipart(String key, String nativeUploadId) {
        if (nativeUploadId == null || nativeUploadId.isBlank()) {
            return;
        }
        execute(authorized("DELETE", key, Map.of("uploadId", nativeUploadId), new byte[0]), "放弃分片失败");
    }

    @Override
    public Optional<String> presignedDownload(String key, Duration ttl, String fileName, String contentType) {
        Map<String, String> query = new LinkedHashMap<>();
        if (fileName != null && !fileName.isBlank()) {
            query.put("response-content-disposition", "attachment; filename=\"" + fileName + "\"");
        }
        if (contentType != null && !contentType.isBlank()) {
            query.put("response-content-type", contentType);
        }
        return Optional.of(presignedUrl("GET", key, query, ttl));
    }

    // ---------- 内部 ----------

    private Request.Builder authorized(String method, String key, Map<String, String> query, byte[] payload) {
        HttpUrl url = url(key, query);
        Instant now = Instant.now();
        Map<String, String> headers = AwsSignatureV4.signHeaders(method, host, url.encodedPath(), query,
                payload, region(), settings.s3AccessKey(), settings.s3SecretKey(), now);
        Request.Builder builder = new Request.Builder().url(url);
        headers.forEach(builder::header);
        if ("PUT".equals(method) || "POST".equals(method)) {
            builder.method(method, RequestBody.create(payload == null ? new byte[0] : payload,
                    MediaType.parse("application/xml")));
        } else {
            builder.method(method, null);
        }
        return builder;
    }

    private String presignedUrl(String method, String key, Map<String, String> query, Duration ttl) {
        HttpUrl url = url(key, query);
        Map<String, String> signed = AwsSignatureV4.presignQuery(method, host, url.encodedPath(), query,
                region(), settings.s3AccessKey(), settings.s3SecretKey(), Instant.now(), ttl);
        return url.newBuilder()
                .encodedQuery(AwsSignatureV4.canonicalQuery(signed))
                .build()
                .toString();
    }

    private String execute(Request.Builder builder, String message) {
        try (Response response = http.newCall(builder.build()).execute()) {
            ResponseBody body = response.body();
            String content = body == null ? null : body.string();
            if (!response.isSuccessful()) {
                throw new BizException(ErrorCode.INTERNAL, message + "（HTTP " + response.code() + "）");
            }
            return content;
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL, message + "：" + ex.getMessage());
        }
    }

    /** 组装对象 URL：path-style 为 {@code /{bucket}/{key}}，virtual-host 为 {@code /{key}}。 */
    private HttpUrl url(String key, Map<String, String> query) {
        HttpUrl base = HttpUrl.get(settings.s3Endpoint());
        HttpUrl.Builder builder = base.newBuilder();
        if (settings.s3PathStyle()) {
            builder.addPathSegment(settings.s3Bucket());
        } else {
            builder.host(settings.s3Bucket() + "." + base.host());
        }
        for (String segment : key.split("/")) {
            if (!segment.isEmpty()) {
                builder.addPathSegment(segment);
            }
        }
        if (query != null && !query.isEmpty()) {
            builder.encodedQuery(AwsSignatureV4.canonicalQuery(query));
        }
        return builder.build();
    }

    private String region() {
        return settings.s3Region() == null || settings.s3Region().isBlank() ? "us-east-1" : settings.s3Region();
    }

    private static String quotedEtag(String etag) {
        if (etag == null || etag.isBlank()) {
            return "";
        }
        String trimmed = etag.trim();
        return trimmed.startsWith("\"") ? trimmed : "\"" + trimmed + "\"";
    }

    private static OkHttpClient defaultClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }
}
