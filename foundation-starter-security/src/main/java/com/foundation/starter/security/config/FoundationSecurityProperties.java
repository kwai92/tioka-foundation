package com.foundation.starter.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** 令牌与当前用户配置（F-07 §13：{@code foundation.security.*}）。 */
@ConfigurationProperties(prefix = "foundation.security")
public class FoundationSecurityProperties {

    /** 令牌来源：{@code jwt}（资源服务器校验）或 {@code opaque}（应用自定义 {@code PrincipalResolver}）。 */
    private String tokenSource = "jwt";

    /** {@code @PublicApi} 之外的免鉴权路径（框架级，慎用）。 */
    private List<String> publicApiPaths = new ArrayList<>(List.of("/actuator/**"));

    /** JWT 验签公钥集地址（{@code jwt} 来源时必填）。 */
    private String jwkSetUri;

    /** JWT 校验的签发者，可空表示不校验。 */
    private String issuer;

    /** JWT 校验的受众（{@code aud}），可空表示不校验。 */
    private String audience;

    /** 身份声明名映射（Foundation 经 Keycloak 签发，F-07 §2）。 */
    private final Claims claims = new Claims();

    public String getTokenSource() {
        return tokenSource;
    }

    public void setTokenSource(String tokenSource) {
        this.tokenSource = tokenSource;
    }

    public List<String> getPublicApiPaths() {
        return publicApiPaths;
    }

    public void setPublicApiPaths(List<String> publicApiPaths) {
        this.publicApiPaths = publicApiPaths;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Claims getClaims() {
        return claims;
    }

    /** 令牌声明名映射。 */
    public static class Claims {

        private String userId = "sub";
        private String personId = "person_id";
        private String accountId = "account_id";
        private String employeeNo = "employee_no";
        private String displayName = "name";
        private String primaryOrgUnit = "primary_org_unit";
        private String appCode = "app_code";

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getPersonId() {
            return personId;
        }

        public void setPersonId(String personId) {
            this.personId = personId;
        }

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public String getEmployeeNo() {
            return employeeNo;
        }

        public void setEmployeeNo(String employeeNo) {
            this.employeeNo = employeeNo;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getPrimaryOrgUnit() {
            return primaryOrgUnit;
        }

        public void setPrimaryOrgUnit(String primaryOrgUnit) {
            this.primaryOrgUnit = primaryOrgUnit;
        }

        public String getAppCode() {
            return appCode;
        }

        public void setAppCode(String appCode) {
            this.appCode = appCode;
        }
    }
}
