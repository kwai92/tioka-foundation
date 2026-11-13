package com.foundation.org.domain;

import com.foundation.org.api.dto.PersonStatus;
import com.foundation.org.api.dto.PersonType;
import com.foundation.org.api.dto.PersonView;

/**
 * 人员（F-03 §2.1）。
 *
 * <p>手机与邮箱只以密文（{@code *_cipher}，AES-256-GCM）与查重哈希（{@code *_hash}）形态存在于本实体；
 * 明文仅在 {@code PersonCipher} 解密后按列表 / 详情分别脱敏出参（F-03 §2.1、§4.2）。
 *
 * @param id           主键（{@code per_…}）
 * @param empNo        工号（唯一，数字员工亦赋工号）
 * @param name         姓名
 * @param type         类型
 * @param status       状态
 * @param mobileCipher 手机密文
 * @param emailCipher  邮箱密文
 * @param mobileHash   手机查重哈希
 * @param emailHash    邮箱查重哈希
 * @param source       来源
 * @param externalId   外部 ID
 * @param version      行版本
 */
public record Person(String id,
                     String empNo,
                     String name,
                     PersonType type,
                     PersonStatus status,
                     String mobileCipher,
                     String emailCipher,
                     String mobileHash,
                     String emailHash,
                     String source,
                     String externalId,
                     long version) {

    /**
     * 转对外视图（明文 / 脱敏由调用方决定后传入）。
     *
     * @param mobile           手机出参值（明文或脱敏）
     * @param email            邮箱出参值（明文或脱敏）
     * @param masked           出参是否脱敏
     * @param primaryOrgUnitId 主任职组织单元 ID
     * @param managerPersonId  直属上级人员 ID
     */
    public PersonView toView(String mobile, String email, boolean masked,
                             String primaryOrgUnitId, String managerPersonId) {
        return new PersonView(id, empNo, name, type, status, mobile, email, masked,
                primaryOrgUnitId, managerPersonId, version);
    }

    /** 是否数字员工。 */
    public boolean digitalEmployee() {
        return type == PersonType.DIGITAL_EMPLOYEE;
    }

    /** 是否可登录（数字员工不产生账号，F-03 §3）。 */
    public boolean loginEnabled() {
        return type != PersonType.DIGITAL_EMPLOYEE && status == PersonStatus.ACTIVE;
    }
}
