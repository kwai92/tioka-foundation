package com.foundation.authz.domain;

/**
 * 互斥角色对（F-05 BAS-05 §有效角色与互斥，如三员互斥）。
 *
 * @param roleCode      角色编码
 * @param mutexRoleCode 与之互斥的角色编码
 */
public record RoleMutex(String roleCode, String mutexRoleCode) {
}
