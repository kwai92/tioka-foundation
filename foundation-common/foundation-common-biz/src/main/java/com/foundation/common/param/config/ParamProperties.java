package com.foundation.common.param.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 参数配置（F-02 BAS-09 §配置项）。 */
@ConfigurationProperties(prefix = "foundation.param")
public class ParamProperties {

    /** 对外读敏感参数时的掩码（管理台展示形态）。 */
    private String secretMask = "******";

    /** 参数值长度上限。 */
    private int maxValueLength = 4096;

    /** 敏感参数主密钥（只写不读，经环境变量注入；空则用开发缺省密钥）。 */
    private String masterKey = "";

    public String getSecretMask() {
        return secretMask;
    }

    public void setSecretMask(String secretMask) {
        this.secretMask = secretMask;
    }

    public int getMaxValueLength() {
        return maxValueLength;
    }

    public void setMaxValueLength(int maxValueLength) {
        this.maxValueLength = maxValueLength;
    }

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }
}
