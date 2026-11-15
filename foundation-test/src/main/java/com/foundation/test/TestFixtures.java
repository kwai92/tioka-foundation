package com.foundation.test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 测试数据构造工具（仅用 JDK，保持 foundation-test 零编译期框架依赖）。
 *
 * <p>时间一律按 UTC 语义构造，与 00-施工总纲 §2.2「时间统一 UTC」一致。
 */
public final class TestFixtures {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final DateTimeFormatter SUFFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private TestFixtures() {
    }

    /** 按 UTC 语义构造时间：各字段即 UTC 墙钟值，不经过系统默认时区换算。 */
    public static LocalDateTime utcOf(int year, int month, int day, int hour, int minute, int second) {
        return LocalDateTime.of(year, month, day, hour, minute, second);
    }

    /** 按 UTC 语义由 epoch 秒构造时间。 */
    public static LocalDateTime utcOf(long epochSecond) {
        return LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC);
    }

    /** 进程内单调递增的唯一后缀（UTC 时间戳 + 自增序号），确定性唯一、不使用随机数。 */
    public static String uniqueSuffix() {
        long sequence = SEQUENCE.incrementAndGet();
        return LocalDateTime.now(ZoneOffset.UTC).format(SUFFIX_FORMATTER) + "-" + sequence;
    }
}
