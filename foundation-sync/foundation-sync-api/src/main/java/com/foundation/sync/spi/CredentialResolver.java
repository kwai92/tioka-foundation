package com.foundation.sync.spi;

/**
 * 凭证解析器（ADR-042 §1.6 / ADR-042 §7 F16）。
 *
 * <p>把 {@link SyncCredentialsRef#secretRef()} 这样的「密钥引用」解析成明文密钥值。
 * 引用的存在意义是**明文不落配置、不落同步来源行**：{@code fdn_sync_source.credential_ref}
 * 只存引用（见 {@code V25__sync_source.sql}），明文由平台侧加密保管。
 *
 * <p><b>为什么是接口而非直接调用加密器</b>：ADR-042 §7 的前向兼容约束 F16 要求
 * 「取密钥只经一个『凭证解析』接口，M3 换成多 Key 选用时调用方不改」。当前默认实现
 * （{@code ParamCredentialResolver}）解析到平台参数中心（{@code ParamApi.secretValue}）；
 * 将来 MH-12 落地独立凭证实体（多 Key、有效期、轮换、吊销）时**只需替换本接口的实现**，
 * 适配器与同步引擎一行都不用改。
 *
 * <p><b>依赖边界</b>：本接口位于 {@code foundation-sync-api}，故插件适配器可以依赖它；
 * 实现位于 {@code foundation-sync-biz}（依赖 {@code foundation-common-api} 的 {@code ParamApi}）。
 * 适配器**不得**依赖 {@code -biz}（ADR-078 第 8 条），因此适配器侧只能见到本接口，
 * 由 Spring 在运行期注入实现；实现缺失时适配器按「未装载解析器」处理。
 */
public interface CredentialResolver {

    /**
     * 解析密钥引用为明文。
     *
     * <p>约定引用格式 {@code secret://{scope}/{key}}（例：{@code secret://foundation/feishu-app-secret}），
     * 其中 {@code scope} 为参数作用域、{@code key} 为参数键。格式非法或密钥不存在时抛
     * {@code BizException}，**不得**返回引用原文兜底——把引用字符串当密码用会造成难以定位的认证失败。
     *
     * <p>本接口所在模块为零依赖契约模块，故此处以文本而非 {@code @link} 指代
     * {@code com.foundation.common.api.error.BizException}（属 {@code foundation-common-api}）；
     * 实现方须抛该异常，调用方按平台统一异常语义处理。
     *
     * @param secretRef 密钥引用；{@code null} 或空白视为「未配置引用」
     * @return 明文密钥值
     * @throws RuntimeException 引用格式非法（{@code BAD_REQUEST}）或密钥不存在（{@code NOT_FOUND}）
     *         时抛 {@code BizException}
     */
    String resolve(String secretRef);

    /**
     * 该解析器是否支持给定引用。适配器据此决定「走解析」还是「回落本地明文」。
     *
     * <p>默认实现要求引用非空且以 {@code secret://} 开头；实现可覆盖以支持其它密钥来源
     * （如 KMS、Vault、Kubernetes Secret）。
     */
    default boolean supports(String secretRef) {
        return secretRef != null && !secretRef.isBlank() && secretRef.startsWith("secret://");
    }
}
