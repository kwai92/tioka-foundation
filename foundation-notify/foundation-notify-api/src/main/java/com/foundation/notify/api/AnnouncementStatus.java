package com.foundation.notify.api;

/** 公告状态（F-09 BAS-16 §数据模型 {@code fdn_announcement.status}）。 */
public enum AnnouncementStatus {

    DRAFT,
    PUBLISHED,
    OFFLINE;

    /** 宽松解析；空值与无法识别时返回 {@code null}（表示不过滤）。 */
    public static AnnouncementStatus of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (AnnouncementStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return null;
    }
}
