package com.foundation.account.api;

import com.foundation.account.api.dto.AccountCreateCmd;
import com.foundation.account.api.dto.AccountReconcileView;
import com.foundation.account.api.dto.AccountResetResult;
import com.foundation.account.api.dto.AccountSessionView;
import com.foundation.account.api.dto.AccountStatus;
import com.foundation.account.api.dto.AccountType;
import com.foundation.account.api.dto.AccountView;
import com.foundation.account.api.dto.EmergencyEnableTicket;
import com.foundation.account.api.dto.PageResult;
import com.foundation.account.api.dto.ServiceAccountCreateCmd;

import java.util.List;
import java.util.Optional;

/**
 * 账号契约（F-04 BAS-03）：其他模块（BAS-14 离职联动等）只依赖本接口。
 *
 * <p>所有写操作先落 Foundation 库、再推送 Keycloak（F-04 §5）；Foundation 不保存口令。
 */
public interface AccountApi {

    /** 按账号 ID 取账号。 */
    Optional<AccountView> findById(String accountId);

    /** 按人员取个人账号（一人最多一个）。 */
    Optional<AccountView> findByPersonId(String personId);

    /** 按登录名取账号。 */
    Optional<AccountView> findByLoginName(String loginName);

    /** 账号列表（个人与应急管理员；{@code q} 匹配登录名，可空）。 */
    PageResult<AccountView> search(String q, AccountStatus status, AccountType type, int page, int size);

    /** 开通个人账号并推送 Keycloak，返回账号 ID（{@code acc_…}）。 */
    String createPersonal(AccountCreateCmd cmd);

    /** 停用账号并吊销会话，推送 Keycloak {@code enabled=false}。 */
    void disable(String accountId, String operator);

    /** 重新开通账号（{@code status=NORMAL}）。 */
    void enable(String accountId, String operator);

    /** 锁定账号。 */
    void lock(String accountId, String operator);

    /** 解锁账号。 */
    void unlock(String accountId, String operator);

    /** 重置口令：发重置邮件或返回一次性临时口令（临时口令不落库、不进日志）。 */
    AccountResetResult resetPassword(String accountId, String operator);

    /** 账号在线会话（来自 Keycloak）。 */
    List<AccountSessionView> sessions(String accountId);

    /** 强制下线并吊销全部会话，发 {@code fdn.account.sessions_revoked}。 */
    void revokeSessions(String accountId, String operator);

    /** 应急管理员启用第一步（发起）：返回一次性凭据。 */
    EmergencyEnableTicket requestEmergencyEnable(String accountId, String operator);

    /** 应急管理员启用第二步（另一名系统管理员确认，四眼）。 */
    void confirmEmergencyEnable(String accountId, String requestId, String operator);

    /** 新建服务账号（账号主体为本模块 {@code type=SERVICE} 账号，客户端由 BAS-04 开通），返回账号 ID。 */
    String createServiceAccount(ServiceAccountCreateCmd cmd);

    /** 服务账号列表（按应用编码，可空表示全部）。 */
    List<AccountView> listServiceAccounts(String appCode);

    /** 按负责人列出服务账号（BAS-14 交接待办用）。 */
    List<AccountView> serviceAccountsByOwner(String ownerPersonId);

    /** 停用服务账号。 */
    void disableServiceAccount(String accountId, String operator);

    /** 启用服务账号。 */
    void enableServiceAccount(String accountId, String operator);

    /** 最近的每日对账记录（F-04 §6）。 */
    List<AccountReconcileView> recentReconciles(int limit);
}
