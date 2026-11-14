package com.foundation.file.domain;

/**
 * 病毒扫描状态（F-09 BAS-17）。
 *
 * <p>占位实现（{@code foundation.file.virus-scan=placeholder}）落 {@link #SKIPPED}；
 * 接入 ClamAV 等后由 {@link com.foundation.file.scan.VirusScanHook} 落 {@link #PENDING} /
 * {@link #CLEAN} / {@link #INFECTED}。
 */
public enum ScanStatus {

    /** 待扫描。 */
    PENDING,

    /** 已扫描且干净。 */
    CLEAN,

    /** 检出恶意内容：禁止下载。 */
    INFECTED,

    /** 未启用扫描（占位实现）。 */
    SKIPPED;

    /** 宽松解析；无法识别取 {@link #SKIPPED}（与库默认一致）。 */
    public static ScanStatus of(String value) {
        if (value != null) {
            for (ScanStatus status : values()) {
                if (status.name().equalsIgnoreCase(value.trim())) {
                    return status;
                }
            }
        }
        return SKIPPED;
    }

    /** 是否允许下载（{@link #INFECTED} 一律拒绝，F-09 BAS-17）。 */
    public boolean downloadable() {
        return this != INFECTED;
    }
}
