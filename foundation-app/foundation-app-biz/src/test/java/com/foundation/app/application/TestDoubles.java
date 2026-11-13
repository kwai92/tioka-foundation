package com.foundation.app.application;

import com.foundation.app.domain.App;
import com.foundation.app.domain.AppClient;
import com.foundation.app.domain.ServiceAccount;
import com.foundation.app.infrastructure.AppClientRepository;
import com.foundation.app.infrastructure.AppRepository;
import com.foundation.app.infrastructure.ServiceAccountRepository;
import com.foundation.common.api.keycloak.KeycloakOidcClientAdmin;
import com.foundation.common.api.keycloak.OidcClientSpec;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** BAS-04 应用层单测用的内存仓储与 Keycloak 桩。 */
final class TestDoubles {

    private TestDoubles() {
    }

    static final class InMemoryAppRepository implements AppRepository {

        final Map<String, App> byId = new LinkedHashMap<>();

        @Override
        public List<App> list() {
            return List.copyOf(byId.values());
        }

        @Override
        public Optional<App> find(String code) {
            return byId.values().stream().filter(app -> app.code().equals(code)).findFirst();
        }

        @Override
        public Optional<App> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void insert(App app, String operator) {
            byId.put(app.id(), app);
        }

        @Override
        public void update(App app, String operator) {
            byId.put(app.id(), app);
        }
    }

    static final class InMemoryAppClientRepository implements AppClientRepository {

        final Map<String, AppClient> byId = new LinkedHashMap<>();

        @Override
        public List<AppClient> listByAppId(String appId) {
            return byId.values().stream().filter(client -> client.appId().equals(appId)).toList();
        }

        @Override
        public Optional<AppClient> find(String appId, String clientId) {
            return byId.values().stream()
                    .filter(client -> client.appId().equals(appId) && client.clientId().equals(clientId))
                    .findFirst();
        }

        @Override
        public Optional<AppClient> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void insert(AppClient client, String operator) {
            byId.put(client.id(), client);
        }

        @Override
        public void updateStatus(String id, String status, String operator) {
            AppClient client = byId.get(id);
            byId.put(id, new AppClient(client.id(), client.appId(), client.clientId(), client.redirectUris(),
                    client.grantTypes(), status, client.secretRotatedAt()));
        }

        @Override
        public void markSecretRotated(String id, Instant rotatedAt, String operator) {
            byId.put(id, byId.get(id).secretRotatedAt(rotatedAt));
        }
    }

    static final class InMemoryServiceAccountRepository implements ServiceAccountRepository {

        final Map<String, ServiceAccount> byId = new LinkedHashMap<>();

        @Override
        public List<ServiceAccount> listByAppId(String appId) {
            return byId.values().stream().filter(account -> account.appId().equals(appId)).toList();
        }

        @Override
        public Optional<ServiceAccount> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<ServiceAccount> findByName(String appId, String name) {
            return byId.values().stream()
                    .filter(account -> account.appId().equals(appId) && account.name().equals(name))
                    .findFirst();
        }

        @Override
        public void insert(ServiceAccount serviceAccount, String operator) {
            byId.put(serviceAccount.id(), serviceAccount);
        }

        @Override
        public void updateStatus(String id, String status, String operator) {
            ServiceAccount account = byId.get(id);
            byId.put(id, new ServiceAccount(account.id(), account.accountId(), account.appId(),
                    account.clientId(), account.name(), account.permissionCodes(), account.ownerUserId(),
                    status, account.expiresAt()));
        }

        @Override
        public int disableExpired(Instant now) {
            List<String> expired = byId.values().stream()
                    .filter(account -> ServiceAccount.STATUS_ACTIVE.equals(account.status()))
                    .filter(account -> account.expiredAt(now))
                    .map(ServiceAccount::id)
                    .toList();
            expired.forEach(id -> updateStatus(id, ServiceAccount.STATUS_DISABLED, "system"));
            return expired.size();
        }
    }

    static final class RecordingKeycloak implements KeycloakOidcClientAdmin {

        final List<OidcClientSpec> upserts = new ArrayList<>();
        final List<String> regenerated = new ArrayList<>();
        final List<String> deleted = new ArrayList<>();

        String nextSecret = "generated-secret";
        RuntimeException failOnUpsert;

        @Override
        public void upsert(OidcClientSpec spec) {
            if (failOnUpsert != null) {
                throw failOnUpsert;
            }
            upserts.add(spec);
        }

        @Override
        public String regenerateSecret(String clientId) {
            regenerated.add(clientId);
            return nextSecret;
        }

        @Override
        public void delete(String clientId) {
            deleted.add(clientId);
        }
    }
}
