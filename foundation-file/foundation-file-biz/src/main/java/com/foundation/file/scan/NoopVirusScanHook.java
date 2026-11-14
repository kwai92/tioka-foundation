package com.foundation.file.scan;

import com.foundation.file.domain.ScanStatus;

/**
 * 病毒扫描占位实现（F-09 BAS-17）：本期 {@code foundation.file.virus-scan=placeholder}，
 * 只把 {@code scan_status} 置 {@link ScanStatus#SKIPPED}，不阻断上传与下载。
 */
public class NoopVirusScanHook implements VirusScanHook {

    @Override
    public ScanStatus scan(String fileName, byte[] content) {
        return ScanStatus.SKIPPED;
    }

    @Override
    public String name() {
        return "placeholder";
    }
}
