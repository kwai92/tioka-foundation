package com.foundation.authz.api;

/**
 * 快照重算结果（F-06 BAS-07 §接口）。
 *
 * @param recomputed 实际重算的快照数
 */
public record SnapshotRecomputeResult(int recomputed) {
}
