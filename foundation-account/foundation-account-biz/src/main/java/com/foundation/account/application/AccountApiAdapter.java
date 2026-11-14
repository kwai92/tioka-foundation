package com.foundation.account.application;

import com.foundation.account.api.AccountApi;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@link AccountApi} 契约实现（F-04 §7）：供 BAS-14 离职联动等模块依赖。
 *
 * <p>纯编排：读写全部委托 {@link AccountApplicationService} 与 {@link ServiceAccountApplicationService}。
 */
public class AccountApiAdapter implements AccountApi {

    private final AccountApplicationService accounts;
    private final ServiceAccountApplicationService serviceAccounts;

    public AccountApiAdapter(AccountApplicationService accounts, ServiceAccountApplicationService serviceAccounts) {
        this.accounts = accounts;
        this.serviceAccounts = serviceAccounts;
    }

    @Override
    public Optional<AccountView> findById(String accountId) {
        return accounts.findById(accountId);
    }

    @Override
    public Optional<AccountView> findByPersonId(String personId) {
        return accounts.findByPersonId(personId);
    }

    @Override
    public Optional<AccountView> findByLoginName(String loginName) {
        return accounts.findByLoginName(loginName);
    }

    @Override
    public PageResult<AccountView> search(String q, AccountStatus status, AccountType type, int page, int size) {
        return accounts.search(q, status, type, page, size);
    }

    @Override
    public String createPersonal(AccountCreateCmd cmd) {
        return accounts.createPersonal(cmd);
    }

    @Override
    public void disable(String accountId, String operator) {
        accounts.disable(accountId, operator);
    }

    @Override
    public void enable(String accountId, String operator) {
        accounts.enable(accountId, operator);
    }

    @Override
    public void lock(String accountId, String operator) {
        accounts.lock(accountId, operator);
    }

    @Override
    public void unlock(String accountId, String operator) {
        accounts.unlock(accountId, operator);
    }

    @Override
    public AccountResetResult resetPassword(String accountId, String operator) {
        return accounts.resetPassword(accountId, operator);
    }

    @Override
    public List<AccountSessionView> sessions(String accountId) {
        return accounts.sessions(accountId);
    }

    @Override
    public void revokeSessions(String accountId, String operator) {
        accounts.revokeSessions(accountId, operator);
    }

    @Override
    public EmergencyEnableTicket requestEmergencyEnable(String accountId, String operator) {
        return accounts.requestEmergencyEnable(accountId, operator);
    }

    @Override
    public void confirmEmergencyEnable(String accountId, String requestId, String operator) {
        accounts.confirmEmergencyEnable(accountId, requestId, operator);
    }

    @Override
    public String createServiceAccount(ServiceAccountCreateCmd cmd) {
        return serviceAccounts.create(cmd);
    }

    @Override
    public List<AccountView> listServiceAccounts(String appCode) {
        return serviceAccounts.list(appCode);
    }

    @Override
    public List<AccountView> serviceAccountsByOwner(String ownerPersonId) {
        return serviceAccounts.listByOwner(ownerPersonId);
    }

    @Override
    public void disableServiceAccount(String accountId, String operator) {
        serviceAccounts.disable(accountId, operator);
    }

    @Override
    public void enableServiceAccount(String accountId, String operator) {
        serviceAccounts.enable(accountId, operator);
    }

    @Override
    public List<AccountReconcileView> recentReconciles(int limit) {
        return accounts.recentReconciles(limit);
    }

    @Override
    public void recordLogin(String loginName, Instant at) {
        accounts.recordLogin(loginName, at);
    }
}
