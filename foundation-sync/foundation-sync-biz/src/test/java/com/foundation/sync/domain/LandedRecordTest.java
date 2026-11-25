package com.foundation.sync.domain;

import com.foundation.sync.spi.SyncKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LandedRecord} 对 {@code fields} 的不可变性与 <b>null 值容忍</b>（F-08 BAS-11 §7）。
 *
 * <p>回归背景：本类曾因构造器用 {@code Map.copyOf(fields)} 而对 null 值抛 NPE。
 * {@code MybatisMasterDataPort.personFields} 会把 {@code fdn_person} 的可空列
 * （{@code mobileCipher}/{@code emailCipher}/{@code mobileHash}/{@code emailHash}）原样放入 fields，
 * 而「人员没有手机号或邮箱」是完全正常的业务态；于是同步引擎读取本地人员
 * （{@code existingPersons} / {@code DiffPreviewService.preview} 调 {@code listBySource}）时抛 NPE，
 * 该异常被 {@code SyncEngine.execute} 吞成任务 {@code FAILED}，使<b>删除保护暂停路径永远走不通</b>。
 *
 * <p>该缺陷由 {@code GfSyncGateIT} 在真实 PostgreSQL 上暴露，但闸门需 Docker（CI gate job 才跑），
 * 故在此补离线单测把回归信号变成任何机器都能自动判定的（判据③）。
 */
class LandedRecordTest {

    private static final SyncKey KEY = SyncKey.of("FEISHU", "u-1");

    @Test
    @DisplayName("fields 的值可为 null（人员无手机/邮箱是正常业务态，不得抛 NPE）")
    void toleratesNullFieldValues() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("empNo", "E001");
        fields.put("name", "张三");
        fields.put("mobileCipher", null);
        fields.put("emailCipher", null);
        fields.put("mobileHash", null);
        fields.put("emailHash", null);

        LandedRecord record = new LandedRecord("per_1", KEY, 0L, fields, false, null);

        assertThat(record.fields())
                .as("键集必须完整保留（含值为 null 的键），否则差异比对会把「本地无该字段」与「本地该字段为空」混淆")
                .containsOnlyKeys("empNo", "name", "mobileCipher", "emailCipher", "mobileHash", "emailHash");
        assertThat(record.fields().get("mobileCipher"))
                .as("null 值应原样保留")
                .isNull();
        assertThat(record.fields().get("empNo")).isEqualTo("E001");
    }

    @Test
    @DisplayName("fields 为 null 时归一为空 Map（不得抛 NPE）")
    void nullFieldsBecomesEmptyMap() {
        LandedRecord record = new LandedRecord("per_1", KEY, 0L, null, false, null);

        assertThat(record.fields()).isEmpty();
    }

    @Test
    @DisplayName("fields 保持不可变：外部修改原 Map 不影响记录，且直接 put 抛异常")
    void fieldsAreImmutableAndDefensivelyCopied() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", "张三");
        LandedRecord record = new LandedRecord("per_1", KEY, 0L, fields, false, null);

        // 防御性复制：构造后修改原 Map 不得影响记录
        fields.put("name", "李四");
        fields.put("injected", "值");
        assertThat(record.fields())
                .as("构造后修改入参 Map 不得影响记录（须防御性复制）")
                .containsEntry("name", "张三")
                .doesNotContainKey("injected");

        // 不可变：直接改记录持有的 Map 应失败
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> record.fields().put("x", "y"),
                "fields 必须不可变，否则同步引擎的差异比对结果可被外部篡改");
    }
}
