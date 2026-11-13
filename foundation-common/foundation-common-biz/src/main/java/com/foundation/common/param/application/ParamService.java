package com.foundation.common.param.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.event.CommonEventTypes;
import com.foundation.common.api.param.ParamApi;
import com.foundation.common.api.param.ParamCmd;
import com.foundation.common.id.CommonIdPrefix;
import com.foundation.common.id.Ids;
import com.foundation.common.outbox.EventProperties;
import com.foundation.common.outbox.OutboxAppender;
import com.foundation.common.param.config.ParamProperties;
import com.foundation.common.param.domain.Param;
import com.foundation.common.param.infrastructure.ParamCipher;
import com.foundation.common.param.infrastructure.ParamHistoryRepository;
import com.foundation.common.param.infrastructure.ParamRepository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/** 参数应用服务（F-02 BAS-09）：作用域 CRUD + 敏感值信封加密 + 变更留摘要。 */
public class ParamService implements ParamApi {

    private final ParamRepository params;
    private final ParamHistoryRepository history;
    private final ParamCipher cipher;
    private final OutboxAppender outbox;
    private final EventProperties events;
    private final ParamProperties properties;

    public ParamService(ParamRepository params, ParamHistoryRepository history, ParamCipher cipher,
                        OutboxAppender outbox, EventProperties events, ParamProperties properties) {
        this.params = params;
        this.history = history;
        this.cipher = cipher;
        this.outbox = outbox;
        this.events = events;
        this.properties = properties;
    }

    @Override
    public String value(String appCode, String key) {
        Param param = resolve(appCode, key);
        if (param.secret()) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "敏感参数只写不读（对外展示为 " + properties.getSecretMask() + "）：" + key);
        }
        return param.storedValue();
    }

    @Override
    public String secretValue(String appCode, String key) {
        Param param = resolve(appCode, key);
        return param.secret() ? cipher.decrypt(param.storedValue()) : param.storedValue();
    }

    @Override
    @Transactional
    public void set(ParamCmd cmd) {
        String key = requireText(cmd.key(), "key");
        if (cmd.value() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "value 不能为空");
        }
        if (cmd.value().length() > properties.getMaxValueLength()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "参数值长度超过上限 " + properties.getMaxValueLength());
        }
        String scope = normalize(cmd.appCode());
        String operator = operatorOf(cmd.operator());
        boolean secret = Boolean.TRUE.equals(cmd.secret());

        Optional<Param> existing = params.find(scope, key);
        String id = existing.map(Param::id).orElseGet(() -> Ids.newId(CommonIdPrefix.PARAM));
        Param param = new Param(id, scope, key, secret ? cipher.encrypt(cmd.value()) : cmd.value(),
                secret, cmd.description());
        String oldDigest = existing.map(previous -> digest(plainOf(previous))).orElse(null);
        existing.ifPresentOrElse(previous -> params.update(param, operator), () -> params.insert(param, operator));

        history.append(id, oldDigest, digest(cmd.value()), operator);
        outbox.append(events.getTopic(), CommonEventTypes.PARAM_CHANGED, id, changed(id));
    }

    private Param resolve(String appCode, String key) {
        String scope = normalize(appCode);
        Optional<Param> found = scope == null
                ? params.find(null, key)
                : params.find(scope, key).or(() -> params.find(null, key));
        return found.orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "参数不存在：" + key));
    }

    private String plainOf(Param param) {
        return param.secret() ? cipher.decrypt(param.storedValue()) : param.storedValue();
    }

    private static String digest(String plaintext) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private static Map<String, Object> changed(String id) {
        return Map.of("id", id, "version", 1, "changeType", "UPSERT");
    }

    private static String normalize(String appCode) {
        return appCode == null || appCode.isBlank() ? null : appCode;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, field + " 不能为空");
        }
        return value;
    }

    private static String operatorOf(String operator) {
        return operator == null || operator.isBlank() ? "system" : operator;
    }
}
