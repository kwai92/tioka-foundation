package com.foundation.common.param.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.event.CommonEventTypes;
import com.foundation.common.api.param.ParamCmd;
import com.foundation.common.api.event.EventProperties;
import com.foundation.common.api.event.OutboxAppender;
import com.foundation.common.param.config.ParamProperties;
import com.foundation.common.param.domain.Param;
import com.foundation.common.param.infrastructure.ParamCipher;
import com.foundation.common.param.infrastructure.ParamHistoryRepository;
import com.foundation.common.param.infrastructure.ParamRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-02 BAS-09：参数加密存储、对外只写不读、修改留摘要。 */
class ParamServiceTest {

    private final InMemoryParamRepository params = new InMemoryParamRepository();
    private final RecordingHistory history = new RecordingHistory();
    private final ParamCipher cipher = new ParamCipher() {
        @Override
        public String encrypt(String plaintext) {
            return "ENC(" + plaintext + ")";
        }

        @Override
        public String decrypt(String ciphertext) {
            return ciphertext.substring(4, ciphertext.length() - 1);
        }
    };
    private final RecordingOutbox outbox = new RecordingOutbox();
    private final ParamProperties properties = new ParamProperties();

    private ParamService service() {
        return new ParamService(params, history, cipher, outbox, new EventProperties(), properties);
    }

    @Test
    void normalParamIsReadable() {
        ParamService service = service();
        service.set(new ParamCmd(null, "site.title", "Tioka", null, "站点标题", "alice"));

        assertThat(service.value(null, "site.title")).isEqualTo("Tioka");
    }

    @Test
    void secretParamIsWriteOnly() {
        ParamService service = service();
        service.set(new ParamCmd(null, "db.password", "p@ssw0rd", true, "数据库密码", "alice"));

        assertThatThrownBy(() -> service.value(null, "db.password"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        assertThat(service.secretValue(null, "db.password")).isEqualTo("p@ssw0rd");
    }

    @Test
    void secretParamStoredAsCiphertext() {
        ParamService service = service();
        service.set(new ParamCmd(null, "db.password", "p@ssw0rd", true, "数据库密码", "alice"));

        Param stored = params.byId.values().iterator().next();
        assertThat(stored.storedValue()).isEqualTo("ENC(p@ssw0rd)");
        assertThat(stored.secret()).isTrue();
    }

    @Test
    void appScopedParamWinsOverGlobal() {
        ParamService service = service();
        service.set(new ParamCmd(null, "site.title", "Tioka", null, null, "alice"));
        service.set(new ParamCmd("tioka", "site.title", "Tioka Enterprise", null, null, "alice"));

        assertThat(service.value("tioka", "site.title")).isEqualTo("Tioka Enterprise");
        assertThat(service.value(null, "site.title")).isEqualTo("Tioka");
    }

    @Test
    void updateWritesHistoryWithDigests() {
        ParamService service = service();
        service.set(new ParamCmd(null, "db.password", "old-pass", true, null, "alice"));
        service.set(new ParamCmd(null, "db.password", "new-pass", true, null, "bob"));

        assertThat(history.entries).hasSize(2);
        assertThat(history.entries.get(0).oldDigest()).isNull();
        assertThat(history.entries.get(0).newDigest()).isNotBlank();
        assertThat(history.entries.get(1).oldDigest()).isNotBlank();
        assertThat(history.entries.get(1).newDigest()).isNotBlank();
        assertThat(history.entries.get(0).newDigest())
                .isEqualTo(history.entries.get(1).oldDigest());
    }

    @Test
    void changeEventAppendedWithIdVersionChangeType() {
        ParamService service = service();
        service.set(new ParamCmd(null, "site.title", "Tioka", null, null, "alice"));

        assertThat(outbox.appended).hasSize(1);
        RecordingOutbox.Append append = outbox.appended.get(0);
        assertThat(append.eventType()).isEqualTo(CommonEventTypes.PARAM_CHANGED);
        assertThat(append.payload()).containsOnlyKeys("id", "version", "changeType");
    }

    @Test
    void missingParamThrowsNotFound() {
        assertThatThrownBy(() -> service().value(null, "missing.key"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void rejectsOversizedValue() {
        properties.setMaxValueLength(8);
        assertThatThrownBy(() -> service().set(
                new ParamCmd(null, "too.long", "0123456789", null, null, "alice")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    private static final class InMemoryParamRepository implements ParamRepository {

        private final Map<String, Param> byId = new java.util.LinkedHashMap<>();

        @Override
        public Optional<Param> find(String appCode, String paramKey) {
            return byId.values().stream()
                    .filter(p -> (appCode == null ? p.appCode() == null : appCode.equals(p.appCode()))
                            && p.paramKey().equals(paramKey))
                    .findFirst();
        }

        @Override
        public void insert(Param param, String operator) {
            byId.put(param.id(), param);
        }

        @Override
        public void update(Param param, String operator) {
            byId.put(param.id(), param);
        }
    }

    private static final class RecordingHistory implements ParamHistoryRepository {

        private final List<Entry> entries = new ArrayList<>();

        @Override
        public void append(String paramId, String oldDigest, String newDigest, String operator) {
            entries.add(new Entry(paramId, oldDigest, newDigest, operator));
        }

        private record Entry(String paramId, String oldDigest, String newDigest, String operator) {
        }
    }

    private static final class RecordingOutbox implements OutboxAppender {

        private final List<Append> appended = new ArrayList<>();

        @Override
        public void append(String topic, String eventType, String aggregate, Map<String, Object> payload) {
            appended.add(new Append(topic, eventType, aggregate, payload));
        }

        private record Append(String topic, String eventType, String aggregate, Map<String, Object> payload) {
        }
    }
}
