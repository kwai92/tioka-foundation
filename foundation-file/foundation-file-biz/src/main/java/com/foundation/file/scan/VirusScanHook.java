package com.foundation.file.scan;

import com.foundation.file.domain.ScanStatus;

/**
 * 病毒扫描挂点（SPI，F-09 BAS-17）：接入 ClamAV 等时替换 {@link NoopVirusScanHook}，
 * 由 {@code foundation.file.virus-scan} 选择实现。
 *
 * <p>实现返回 {@link ScanStatus}；{@code INFECTED} 的文件禁止下载（由
 * {@link com.foundation.file.application.FileService} 拦截）。
 */
public interface VirusScanHook {

    /** 扫描内容并给出状态。 */
    ScanStatus scan(String fileName, byte[] content);

    /** 挂点名称（用于配置核对与日志）。 */
    String name();
}
