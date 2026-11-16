package com.foundation.starter.core.contract;

/**
 * 对外 REST 接口的主版本契约（36 号规划 ADP-03）。
 *
 * <p>服务端与所有 starter 客户端共用本常量：客户端每次请求都带
 * {@code X-Foundation-Api-Version: 1}，服务端在过滤器里校验主版本并回写同一响应头。
 * <b>请求头缺省视为 v1</b>，保证旧接入方与历史脚本零改动；只有显式声明了服务端不支持的
 * 主版本时才拒绝，且返回明确的错误码而不是 500。
 *
 * <p>版本只到主版本粒度：新增字段、新增接口、新增可选请求参数都是兼容变更，不升主版本；
 * 删字段、改语义、改枚举、删接口才升主版本，并要求一个并行发布期（见 docs/contracts/README.md）。
 */
public final class FoundationApiVersion {

    /** 版本请求/响应头。 */
    public static final String HEADER = "X-Foundation-Api-Version";

    /** 当前服务端主版本。 */
    public static final int CURRENT_MAJOR = 1;

    /** 当前主版本的头值。 */
    public static final String CURRENT = String.valueOf(CURRENT_MAJOR);

    /** 版本不兼容时的业务错误码。 */
    public static final String UNSUPPORTED_CODE = "FOUNDATION_API_VERSION_UNSUPPORTED";

    /** 版本头格式非法时的业务错误码。 */
    public static final String MALFORMED_CODE = "FOUNDATION_API_VERSION_MALFORMED";

    private FoundationApiVersion() {
    }

    /**
     * 解析主版本；接受 {@code 1} / {@code v1} / {@code V1}。
     *
     * @return 主版本号；格式非法返回 {@code null}
     */
    public static Integer parseMajor(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > 1 && (value.charAt(0) == 'v' || value.charAt(0) == 'V')) {
            value = value.substring(1);
        }
        try {
            int major = Integer.parseInt(value);
            return major > 0 ? major : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
