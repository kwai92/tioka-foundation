package com.foundation.sync.infrastructure;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.param.ParamApi;
import com.foundation.sync.spi.CredentialResolver;

/**
 * 基于平台参数中心的凭证解析器（ADR-042 §1.6，F-08 BAS-11 §9）。
 *
 * <p>把 {@code secret://{scope}/{key}} 解析为明文：调用 {@link ParamApi#secretValue(String, String)}，
 * 由参数中心负责密文存储与解密。该路径**复用既有加密设施**，不新增第二套 cipher：
 * {@code fdn_param.param_value} 在 {@code secret=true} 时存 AES-256-GCM 密文
 * （{@code AesGcmParamCipher}：随机 IV、128 位认证标签、{@code v1:} 版本号信封），
 * 主密钥来自 {@code foundation.param.master-key}（生产缺失即启动失败）。
 *
 * <p><b>作用域语义</b>：{@code {scope}} 直接作为 {@code ParamApi} 的 appCode 传入。
 * 参数中心的作用域解析是「应用项优先、回落全局项」，故把凭证登记为全局项
 * （{@code secret://foundation/xxx} 而库里存全局）与登记为该作用域下的应用项**都能解析成功**，
 * 无需在本地对 {@code foundation} 做特例判断——避免两处作用域语义并存。
 *
 * <p><b>失败即失败，不回落明文</b>：格式非法抛 {@code BAD_REQUEST}，密钥不存在由参数中心抛
 * {@code NOT_FOUND}，密钥为空抛 {@code NOT_FOUND}。**绝不返回引用原文**：把
 * {@code secret://…} 当密码发给对端只会得到难以定位的认证失败
 * （这正是本类替换掉的既有缺陷行为）。
 *
 * <p>ADR-042 §7 前向兼容约束 F16：取密钥只经 {@link CredentialResolver} 一个接口，
 * 故 MH-12 落地独立凭证实体（多 Key、有效期、轮换、吊销、AAD 绑定）时只替换本实现，
 * 适配器与同步引擎不改。
 */
public class ParamCredentialResolver implements CredentialResolver {

    /** 引用协议前缀。 */
    static final String SCHEME = "secret://";

    private final ParamApi params;

    public ParamCredentialResolver(ParamApi params) {
        if (params == null) {
            throw new IllegalArgumentException("ParamApi is required");
        }
        this.params = params;
    }

    @Override
    public String resolve(String secretRef) {
        if (!supports(secretRef)) {
            throw new BizException(ErrorCode.BAD_REQUEST, illegalMessage(secretRef));
        }
        String path = secretRef.substring(SCHEME.length());
        int slash = path.indexOf('/');
        // scope 与 key 都必须非空：缺任一段都无法定位参数项
        if (slash <= 0 || slash == path.length() - 1) {
            throw new BizException(ErrorCode.BAD_REQUEST, illegalMessage(secretRef));
        }
        String scope = path.substring(0, slash);
        String key = path.substring(slash + 1);

        String value = params.secretValue(scope, key);
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "凭证引用指向的密钥为空：" + secretRef);
        }
        return value;
    }

    private static String illegalMessage(String secretRef) {
        return "凭证引用格式非法，应为 " + SCHEME + "{scope}/{key}：" + secretRef;
    }
}
