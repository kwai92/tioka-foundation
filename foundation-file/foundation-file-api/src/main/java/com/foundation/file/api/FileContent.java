package com.foundation.file.api;

import java.io.InputStream;

/**
 * 文件内容（F-09 BAS-17「GET /api/fdn/files/{id}/content」流式下载）。
 *
 * <p>调用方负责关闭 {@code stream}（Controller 交给 {@code StreamingResponseBody} 用 try-with-resources 收口）。
 *
 * @param fileName    原始文件名（用于 {@code Content-Disposition}）
 * @param contentType MIME 类型
 * @param dataLabel   数据标签（ADR-040）
 * @param sizeBytes   字节数
 * @param stream      内容流
 */
public record FileContent(String fileName,
                          String contentType,
                          String dataLabel,
                          long sizeBytes,
                          InputStream stream) {
}
