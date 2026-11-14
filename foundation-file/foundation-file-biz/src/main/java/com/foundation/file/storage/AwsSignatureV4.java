package com.foundation.file.storage;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * AWS Signature V4（F-09 BAS-17 的 S3 兼容后端）：请求头签名与查询串预签名。
 *
 * <p>不引入 AWS SDK，直接按公开算法实现，供 MinIO / 客户 OSS 的 S3 兼容接口使用；
 * 预签名 URL 用于「业务侧直连对象存储」而不发放长期凭证。
 *
 * <p>算法依据：AWS Signature Version 4（{@code AWS4-HMAC-SHA256}）。
 */
public final class AwsSignatureV4 {

    public static final String ALGORITHM = "AWS4-HMAC-SHA256";
    public static final String SERVICE = "s3";
    public static final String TERMINATOR = "aws4_request";
    public static final String CONTENT_SHA256_HEADER = "x-amz-content-sha256";
    public static final String DATE_HEADER = "x-amz-date";
    public static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";

    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter SCOPE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC);
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private AwsSignatureV4() {
    }

    /**
     * 请求头签名（用于服务端发起实际调用）。
     *
     * @return 需要附加到请求的头部：{@code x-amz-date}、{@code x-amz-content-sha256}、{@code Authorization}
     */
    public static Map<String, String> signHeaders(String method,
                                                  String host,
                                                  String canonicalUri,
                                                  Map<String, String> query,
                                                  byte[] payload,
                                                  String region,
                                                  String accessKey,
                                                  String secretKey,
                                                  Instant timestamp) {
        String amzDate = AMZ_DATE.format(timestamp);
        String payloadHash = sha256Hex(payload == null ? new byte[0] : payload);
        String canonicalHeaders = "host:" + host + "\n"
                + CONTENT_SHA256_HEADER + ":" + payloadHash + "\n"
                + DATE_HEADER + ":" + amzDate + "\n";
        String signedHeaders = "host;" + CONTENT_SHA256_HEADER + ";" + DATE_HEADER;
        String canonicalRequest = method + "\n" + canonicalUri + "\n" + canonicalQuery(query) + "\n"
                + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
        String signature = signature(region, accessKey, secretKey, timestamp, canonicalRequest);
        String authorization = ALGORITHM + " Credential=" + accessKey + "/" + scope(timestamp, region)
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(DATE_HEADER, amzDate);
        headers.put(CONTENT_SHA256_HEADER, payloadHash);
        headers.put("Authorization", authorization);
        return headers;
    }

    /**
     * 查询串预签名（用于下载 / 分片直传）：返回需并入请求查询串的参数（含 {@code X-Amz-Signature}）。
     *
     * @param extraQuery 额外查询参数（如 {@code response-content-disposition}、{@code partNumber}），会参与签名
     * @param ttl        有效期
     */
    public static Map<String, String> presignQuery(String method,
                                                   String host,
                                                   String canonicalUri,
                                                   Map<String, String> extraQuery,
                                                   String region,
                                                   String accessKey,
                                                   String secretKey,
                                                   Instant timestamp,
                                                   Duration ttl) {
        Map<String, String> query = new TreeMap<>();
        if (extraQuery != null) {
            query.putAll(extraQuery);
        }
        query.put("X-Amz-Algorithm", ALGORITHM);
        query.put("X-Amz-Credential", accessKey + "/" + scope(timestamp, region));
        query.put("X-Amz-Date", AMZ_DATE.format(timestamp));
        query.put("X-Amz-Expires", String.valueOf(Math.max(1L, ttl.toSeconds())));
        query.put("X-Amz-SignedHeaders", "host");
        String canonicalHeaders = "host:" + host + "\n";
        String canonicalRequest = method + "\n" + canonicalUri + "\n" + canonicalQuery(query) + "\n"
                + canonicalHeaders + "\n" + "host" + "\n" + UNSIGNED_PAYLOAD;
        query.put("X-Amz-Signature", signature(region, accessKey, secretKey, timestamp, canonicalRequest));
        return query;
    }

    /** 规范化查询串：按参数名排序，键值均做 URI 编码。 */
    public static String canonicalQuery(Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        Map<String, String> sorted = new TreeMap<>(query);
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(uriEncode(entry.getKey(), true)).append('=')
                    .append(uriEncode(entry.getValue() == null ? "" : entry.getValue(), true));
        }
        return builder.toString();
    }

    /** 规范化路径：逐段 URI 编码，保留 {@code /}。 */
    public static String canonicalPath(String path) {
        String normalized = path == null || path.isEmpty() ? "/" : path;
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        StringBuilder builder = new StringBuilder();
        for (String segment : normalized.split("/", -1)) {
            builder.append('/').append(uriEncode(segment, true));
        }
        return builder.toString();
    }

    /** RFC 3986 URI 编码：仅保留未保留字符；{@code encodeSlash=false} 时保留 {@code /}。 */
    public static String uriEncode(String value, boolean encodeSlash) {
        StringBuilder builder = new StringBuilder();
        for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
            char ch = (char) (raw & 0xFF);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
                    || ch == '-' || ch == '_' || ch == '.' || ch == '~' || (ch == '/' && !encodeSlash)) {
                builder.append(ch);
            } else {
                builder.append('%').append(Character.toUpperCase(HEX[(raw >> 4) & 0x0F]))
                        .append(Character.toUpperCase(HEX[raw & 0x0F]));
            }
        }
        return builder.toString();
    }

    /** SHA-256 十六进制小写。 */
    public static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes == null ? new byte[0] : bytes);
            char[] out = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                int value = digest[i] & 0xFF;
                out[i * 2] = HEX[value >>> 4];
                out[i * 2 + 1] = HEX[value & 0x0F];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private static String signature(String region, String accessKey, String secretKey,
                                    Instant timestamp, String canonicalRequest) {
        String stringToSign = ALGORITHM + "\n" + AMZ_DATE.format(timestamp) + "\n"
                + scope(timestamp, region) + "\n" + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        byte[] signingKey = hmac(hmac(hmac(hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8),
                SCOPE_DATE.format(timestamp).getBytes(StandardCharsets.UTF_8)), region.getBytes(StandardCharsets.UTF_8)),
                SERVICE.getBytes(StandardCharsets.UTF_8)), TERMINATOR.getBytes(StandardCharsets.UTF_8));
        byte[] signature = hmac(signingKey, stringToSign.getBytes(StandardCharsets.UTF_8));
        char[] out = new char[signature.length * 2];
        for (int i = 0; i < signature.length; i++) {
            int value = signature[i] & 0xFF;
            out[i * 2] = HEX[value >>> 4];
            out[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(out);
    }

    private static String scope(Instant timestamp, String region) {
        return SCOPE_DATE.format(timestamp) + "/" + region + "/" + SERVICE + "/" + TERMINATOR;
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HMAC-SHA256 不可用", ex);
        }
    }
}
