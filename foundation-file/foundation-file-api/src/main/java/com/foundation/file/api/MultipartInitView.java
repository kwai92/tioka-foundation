package com.foundation.file.api;

import java.util.List;

/**
 * 分片上传初始化结果（F-09 BAS-17）：{@code {uploadId, parts[]}}。
 *
 * @param uploadId 上传会话标识（后续 {@code multipart:complete} 凭此收口）
 * @param partSize 单个分片字节数
 * @param parts    分片预签名地址
 */
public record MultipartInitView(String uploadId, long partSize, List<PresignedPartView> parts) {
}
