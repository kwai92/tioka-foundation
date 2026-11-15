package com.foundation.common.dict.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 字典项实体（{@code fdn_dict_item}，F-02 BAS-09 §迁移 V2）。
 *
 * <p>本表只有 {@code create_at}/{@code update_at} 两个时间列，无 {@code create_by}/{@code update_by}，
 * 故不继承审计基类，自行声明字段。{@code ext_json} 为 {@code jsonb}，写入经
 * {@link JsonbStringTypeHandler}（{@code autoResultMap} 使读取也走该处理器）。
 */
@TableName(value = "fdn_dict_item", autoResultMap = true)
public class DictItemDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String typeId;

    private String code;

    private String label;

    private Integer sortNo;

    private String status;

    private String styleTag;

    /** 扩展属性 JSON 文本（DB 列 {@code jsonb}）。 */
    @TableField(value = "ext_json", typeHandler = JsonbStringTypeHandler.class)
    private String extJson;

    /** 创建时间（UTC 语义，列 {@code TIMESTAMPTZ}）。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createAt;

    /** 更新时间（UTC 语义，列 {@code TIMESTAMPTZ}）。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTypeId() {
        return typeId;
    }

    public void setTypeId(String typeId) {
        this.typeId = typeId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStyleTag() {
        return styleTag;
    }

    public void setStyleTag(String styleTag) {
        this.styleTag = styleTag;
    }

    public String getExtJson() {
        return extJson;
    }

    public void setExtJson(String extJson) {
        this.extJson = extJson;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }

    public void setUpdateAt(LocalDateTime updateAt) {
        this.updateAt = updateAt;
    }
}
