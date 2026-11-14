package com.foundation.authz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 子系统配置（F-05 BAS-27 §表与授权模式配置）。 */
@ConfigurationProperties(prefix = "foundation.subsystem")
public class SubsystemProperties {

    /** 子系统首次出现时的默认授权模式。 */
    private String defaultAuthMode = "CONCENTRATED";

    /** 功能开关是否从父向子继承。 */
    private boolean toggleInherit = true;

    public String getDefaultAuthMode() {
        return defaultAuthMode;
    }

    public void setDefaultAuthMode(String defaultAuthMode) {
        this.defaultAuthMode = defaultAuthMode;
    }

    public boolean isToggleInherit() {
        return toggleInherit;
    }

    public void setToggleInherit(boolean toggleInherit) {
        this.toggleInherit = toggleInherit;
    }
}
