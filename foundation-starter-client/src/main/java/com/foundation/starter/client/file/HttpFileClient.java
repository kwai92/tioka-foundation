package com.foundation.starter.client.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.foundation.starter.client.http.FoundationHttpClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文件服务的 HTTP 实现（F-09 BAS-17 §业务集成方式一「SDK / REST」）。
 *
 * <p>经 Foundation REST 收发：上传走 multipart，下载走服务端代理，权限与审计由 Foundation 承担。
 * {@code appCode} 由服务端依当前身份解析，客户端不传。
 */
public class HttpFileClient implements FileClient {

    private static final String PATH = "/api/fdn/files";

    private final FoundationHttpClient http;

    public HttpFileClient(FoundationHttpClient http) {
        this.http = http;
    }

    @Override
    public FileUploadResult upload(String fileName, String contentType, byte[] content, String dataLabel) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (contentType != null && !contentType.isBlank()) {
            fields.put("contentType", contentType);
        }
        if (dataLabel != null && !dataLabel.isBlank()) {
            fields.put("dataLabel", dataLabel);
        }
        JsonNode node = http.postMultipart(PATH, "file", fileName, contentType, content, fields);
        return new FileUploadResult(text(node, "fileId"), text(node, "sha256"),
                node == null ? 0L : node.path("size").asLong(0));
    }

    @Override
    public FileMetadata metadata(String fileId) {
        JsonNode node = http.get(PATH + "/" + fileId);
        return new FileMetadata(text(node, "id"), text(node, "appCode"), text(node, "fileName"),
                text(node, "contentType"), node == null ? 0L : node.path("sizeBytes").asLong(0),
                text(node, "sha256"), text(node, "storageKind"), text(node, "dataLabel"),
                text(node, "scanStatus"), text(node, "status"), text(node, "bizType"),
                text(node, "bizId"), text(node, "uploadedBy"), text(node, "createAt"));
    }

    @Override
    public byte[] download(String fileId) {
        return http.getBytes(PATH + "/" + fileId + "/content");
    }

    @Override
    public SignedDownloadUrl signedUrl(String fileId, String disposition) {
        String path = PATH + "/" + fileId + "/signed-url"
                + (disposition == null || disposition.isBlank() ? "" : "?disposition=" + disposition);
        JsonNode node = http.get(path);
        return new SignedDownloadUrl(text(node, "url"), text(node, "method"), text(node, "expiresAt"));
    }

    @Override
    public void delete(String fileId) {
        http.delete(PATH + "/" + fileId);
    }

    private static String text(JsonNode node, String field) {
        return node == null ? null : node.path(field).asText(null);
    }
}
