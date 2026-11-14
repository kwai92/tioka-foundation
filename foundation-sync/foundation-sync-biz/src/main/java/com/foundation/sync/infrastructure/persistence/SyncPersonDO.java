package com.foundation.sync.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.BaseDO;

/**
 * 人员主数据实体（{@code fdn_person}，F-03 BAS-02）。
 *
 * <p>同步侧自持映射（F-08 BAS-11 §7）：同步引擎直连主数据表做幂等落地，不改 BAS-02 的 {@code -biz}。
 * 比 BAS-02 的 {@code PersonDO} 多一个 {@code local_locked_fields}（V28 新增列）。
 */
@TableName("fdn_person")
public class SyncPersonDO extends BaseDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String empNo;

    private String name;

    private String type;

    private String status;

    private String mobileCipher;

    private String emailCipher;

    private String mobileHash;

    private String emailHash;

    private String source;

    private String externalId;

    private Long version;

    /** 本地锁定字段（逗号分隔），落地时不被来源覆盖。 */
    private String localLockedFields;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmpNo() {
        return empNo;
    }

    public void setEmpNo(String empNo) {
        this.empNo = empNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMobileCipher() {
        return mobileCipher;
    }

    public void setMobileCipher(String mobileCipher) {
        this.mobileCipher = mobileCipher;
    }

    public String getEmailCipher() {
        return emailCipher;
    }

    public void setEmailCipher(String emailCipher) {
        this.emailCipher = emailCipher;
    }

    public String getMobileHash() {
        return mobileHash;
    }

    public void setMobileHash(String mobileHash) {
        this.mobileHash = mobileHash;
    }

    public String getEmailHash() {
        return emailHash;
    }

    public void setEmailHash(String emailHash) {
        this.emailHash = emailHash;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getLocalLockedFields() {
        return localLockedFields;
    }

    public void setLocalLockedFields(String localLockedFields) {
        this.localLockedFields = localLockedFields;
    }
}
