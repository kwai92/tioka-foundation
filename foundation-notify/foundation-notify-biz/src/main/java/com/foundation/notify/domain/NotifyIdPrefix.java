package com.foundation.notify.domain;

/** 消息中心 ID 前缀（F-09 BAS-16 §数据模型）。 */
public final class NotifyIdPrefix {

    public static final String MESSAGE = "msg";
    public static final String RECEIVER = "mrc";
    public static final String TEMPLATE = "mtp";
    public static final String ANNOUNCEMENT = "ann";

    private NotifyIdPrefix() {
    }
}
