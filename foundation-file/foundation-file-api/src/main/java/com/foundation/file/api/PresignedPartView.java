package com.foundation.file.api;

/**
 * 分片预签名地址（F-09 BAS-17）。
 *
 * @param partNumber 分片序号（从 1 起）
 * @param url        上传地址（S3 为对象存储预签名 URL；LOCAL 为 Foundation 自身的分片接收地址）
 */
public record PresignedPartView(int partNumber, String url) {
}
