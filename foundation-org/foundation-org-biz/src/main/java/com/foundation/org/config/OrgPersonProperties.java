package com.foundation.org.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 人员敏感字段脱敏配置（F-03 §4.2、§7.1），前缀 {@code foundation.org.person}。 */
@ConfigurationProperties(prefix = "foundation.org.person")
public class OrgPersonProperties {

    /** 手机 / 邮箱加解密主密钥；留空则用开发缺省主密钥（生产必须经环境变量注入）。 */
    private String masterKey;

    private final Mask mask = new Mask();

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public Mask getMask() {
        return mask;
    }

    /** 脱敏模式。 */
    public static class Mask {

        /** 手机脱敏：{@code tail-4} 保留后 4 位。 */
        private String mobile = "tail-4";

        /** 邮箱脱敏：{@code domain} 保留首字符与 {@code @} 后域名。 */
        private String email = "domain";

        public String getMobile() {
            return mobile;
        }

        public void setMobile(String mobile) {
            this.mobile = mobile;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}
