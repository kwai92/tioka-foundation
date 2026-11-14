package com.foundation.notify.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.BaseDO;

/** 渠道模板实体（{@code fdn_message_template}）；{@code app_code} 为空表示全局模板。 */
@TableName("fdn_message_template")
public class MessageTemplateDO extends BaseDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String appCode;

    private String code;

    private String channel;

    private String titleTpl;

    private String bodyTpl;

    private String dataLabel;

    private String status;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getTitleTpl() {
        return titleTpl;
    }

    public void setTitleTpl(String titleTpl) {
        this.titleTpl = titleTpl;
    }

    public String getBodyTpl() {
        return bodyTpl;
    }

    public void setBodyTpl(String bodyTpl) {
        this.bodyTpl = bodyTpl;
    }

    public String getDataLabel() {
        return dataLabel;
    }

    public void setDataLabel(String dataLabel) {
        this.dataLabel = dataLabel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
