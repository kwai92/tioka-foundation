package com.foundation.org.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.org.api.dto.PersonStatus;
import com.foundation.org.api.dto.PersonType;

/**
 * 数字员工规则（F-03 §3、§7.3）：负责人必填、无登录账号、必挂主任职、下岗置 {@code DISABLED}。
 *
 * <p>数字员工由 Tioka 在 Agent 上岗 / 下岗时经接口维护，档案本身不含任何登录凭据。
 */
public class DigitalEmployeeRule {

    /** 负责人必填。 */
    public void assertOwnerProvided(String ownerPersonId) {
        if (ownerPersonId == null || ownerPersonId.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "数字员工必须指定负责人（owner_id）");
        }
    }

    /** 必须挂主任职（授权书 {@code departmentId} 对应的服务部门）。 */
    public void assertPrimaryEmployment(boolean primary) {
        if (!primary) {
            throw new BizException(ErrorCode.BAD_REQUEST, "数字员工必须挂主任职到其服务部门");
        }
    }

    /** 是否允许开通登录账号：数字员工不产生账号（BAS-03 明确不处理）。 */
    public boolean accountProvisioningAllowed(PersonType type) {
        return type != PersonType.DIGITAL_EMPLOYEE;
    }

    /** 下岗后的人员状态。 */
    public PersonStatus statusOnOffboarding() {
        return PersonStatus.DISABLED;
    }
}
