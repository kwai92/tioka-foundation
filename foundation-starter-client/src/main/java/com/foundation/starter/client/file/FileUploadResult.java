package com.foundation.starter.client.file;

/**
 * 上传结果（F-09 BAS-17）：{@code {fileId, sha256, size}}。
 *
 * @param fileId 文件 ID（{@code fil_…}）
 * @param sha256 内容 SHA-256 十六进制小写
 * @param size   字节数
 */
public record FileUploadResult(String fileId, String sha256, long size) {
}
