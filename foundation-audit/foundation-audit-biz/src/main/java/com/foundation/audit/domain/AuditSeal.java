package com.foundation.audit.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 审计链封印（F-09 BAS-10；GOV-09 / ADR-072 §8）。
 *
 * <p>对窗口内的审计行按 {@code id} 升序规范化后串联，前缀上一封印的 {@code batchHash}，
 * 取 SHA-256 十六进制小写作为本封印的 {@code batchHash}，从而形成可校验的哈希链。
 *
 * @param id         主键（{@code asl_…}）
 * @param windowFrom 窗口起（含）
 * @param windowTo   窗口止（不含）
 * @param prevHash   上一封印的批次哈希，可空（链首）
 * @param batchHash  本封印批次哈希（SHA-256 十六进制小写）
 * @param sealedAt   封印时刻（UTC）
 */
public record AuditSeal(String id, Instant windowFrom, Instant windowTo, String prevHash, String batchHash,
                        Instant sealedAt) {

    /**
     * 计算审计链批次哈希（纯函数，可单测）：篡改任一行都会改变结果。
     *
     * <p>每行规范化为 {@code id|appCode|action|actorId|outcome|ts.toEpochMilli()}，
     * 全部行以 {@code '\n'} 连接，并以前缀 {@code prevHash}（可空则空串）另起一行开头。
     */
    public static String chainHash(String prevHash, List<AuditLog> rows) {
        List<AuditLog> sorted = new ArrayList<>(rows == null ? List.of() : rows);
        sorted.sort(Comparator.comparing(AuditLog::id));
        List<String> lines = new ArrayList<>();
        lines.add(prevHash == null ? "" : prevHash);
        for (AuditLog row : sorted) {
            lines.add(row.id() + "|" + row.appCode() + "|" + row.action() + "|" + row.actorId()
                    + "|" + row.outcome() + "|" + row.ts().toEpochMilli());
        }
        return sha256Hex(String.join("\n", lines));
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
