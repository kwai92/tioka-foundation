package com.foundation.sync.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 同步来源实体（{@code fdn_sync_source}，F-08 BAS-11 §8）。
 *
 * <p>不继承 {@code BaseDO}：本表无 {@code deleted} 列（来源停用走 {@code status=DISABLED}）。
 */
@TableName("fdn_sync_source")
public class SyncSourceDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String code;

    private String name;

    private String sourceType;

    private Boolean authority;

    private String credentialRef;

    private String adapterImpl;

    private String pluginSha256;

    private String capabilitiesJson;

    private String configJson;

    private String status;

    private String scheduleCron;

    private LocalDateTime createAt;

    private LocalDateTime updateAt;

    private String createBy;

    private String updateBy;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Boolean getAuthority() {
        return authority;
    }

    public void setAuthority(Boolean authority) {
        this.authority = authority;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public void setCredentialRef(String credentialRef) {
        this.credentialRef = credentialRef;
    }

    public String getAdapterImpl() {
        return adapterImpl;
    }

    public void setAdapterImpl(String adapterImpl) {
        this.adapterImpl = adapterImpl;
    }

    public String getPluginSha256() {
        return pluginSha256;
    }

    public void setPluginSha256(String pluginSha256) {
        this.pluginSha256 = pluginSha256;
    }

    public String getCapabilitiesJson() {
        return capabilitiesJson;
    }

    public void setCapabilitiesJson(String capabilitiesJson) {
        this.capabilitiesJson = capabilitiesJson;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getScheduleCron() {
        return scheduleCron;
    }

    public void setScheduleCron(String scheduleCron) {
        this.scheduleCron = scheduleCron;
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

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }
}
