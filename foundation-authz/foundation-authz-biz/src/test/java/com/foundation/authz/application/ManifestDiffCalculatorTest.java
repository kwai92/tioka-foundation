package com.foundation.authz.application;

import com.foundation.authz.domain.LifecycleStatus;
import com.foundation.authz.domain.ManifestDiff;
import com.foundation.authz.domain.ManifestDocument;
import com.foundation.authz.domain.ManifestMenu;
import com.foundation.authz.domain.ManifestPermission;
import com.foundation.authz.domain.Menu;
import com.foundation.authz.domain.MenuType;
import com.foundation.authz.domain.PermissionCode;
import com.foundation.authz.domain.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 清单差异三分支（F-05 BAS-05 §测试 {@code ManifestDiffTest}）。 */
class ManifestDiffCalculatorTest {

    private static final String APP_ID = "app_contract";

    private final ManifestDiffCalculator calculator = new ManifestDiffCalculator();

    @Test
    void newDeclarationsAreAdded() {
        ManifestDocument declared = new ManifestDocument(2,
                List.of(menu("contract.list", "/contracts")),
                List.of(permission("contract:read", RiskLevel.LOW)));

        ManifestDiff diff = calculator.diff(declared, List.of(), List.of());

        assertThat(diff.addedMenus()).extracting(ManifestMenu::code).containsExactly("contract.list");
        assertThat(diff.addedPermissions()).extracting(ManifestPermission::code).containsExactly("contract:read");
        assertThat(diff.changedMenus()).isEmpty();
        assertThat(diff.offlineCount()).isZero();
    }

    @Test
    void missingActiveItemsGoOffline() {
        ManifestDocument declared = new ManifestDocument(2, List.of(), List.of());

        ManifestDiff diff = calculator.diff(declared,
                List.of(activeMenu("men_1", "contract.list", "/contracts")),
                List.of(activePermission("pms_1", "contract:read", RiskLevel.LOW)));

        assertThat(diff.offlineMenus()).extracting(Menu::code).containsExactly("contract.list");
        assertThat(diff.offlinePermissions()).extracting(PermissionCode::code).containsExactly("contract:read");
        assertThat(diff.offlineCount()).isEqualTo(2);
    }

    @Test
    void alreadyOfflineItemsAreNotOfflinedAgain() {
        ManifestDocument declared = new ManifestDocument(2, List.of(), List.of());

        ManifestDiff diff = calculator.diff(declared,
                List.of(new Menu("men_1", APP_ID, "contract.list", "合同台账", MenuType.MENU.name(), null,
                        "/contracts", null, null, 0, true, null, LifecycleStatus.OFFLINE)),
                List.of());

        assertThat(diff.offlineMenus()).isEmpty();
    }

    @Test
    void sourceFieldChangeIsDetectedButAdminDisplayEditsAreNot() {
        Menu existing = activeMenu("men_1", "contract.list", "/contracts");

        ManifestDiff routeChanged = calculator.diff(
                new ManifestDocument(2, List.of(menu("contract.list", "/contracts/v2")), List.of()),
                List.of(existing), List.of());
        ManifestDiff unchanged = calculator.diff(
                new ManifestDocument(2, List.of(menu("contract.list", "/contracts")), List.of()),
                List.of(existing), List.of());

        assertThat(routeChanged.changedMenus()).hasSize(1);
        assertThat(unchanged.changedMenus()).isEmpty();
    }

    @Test
    void reDeclaredOfflineItemIsChanged() {
        Menu offline = new Menu("men_1", APP_ID, "contract.list", "合同台账", MenuType.MENU.name(), null,
                "/contracts", null, null, 0, true, null, LifecycleStatus.OFFLINE);

        ManifestDiff diff = calculator.diff(
                new ManifestDocument(2, List.of(menu("contract.list", "/contracts")), List.of()),
                List.of(offline), List.of());

        assertThat(diff.changedMenus()).hasSize(1);
        assertThat(diff.offlineMenus()).isEmpty();
    }

    @Test
    void permissionRiskLevelChangeIsDetected() {
        ManifestDocument declared = new ManifestDocument(2, List.of(),
                List.of(permission("contract:read", RiskLevel.HIGH)));

        ManifestDiff diff = calculator.diff(declared, List.of(),
                List.of(activePermission("pms_1", "contract:read", RiskLevel.LOW)));

        assertThat(diff.changedPermissions()).hasSize(1);
        assertThat(diff.addedPermissions()).isEmpty();
    }

    private static ManifestMenu menu(String code, String routePath) {
        return new ManifestMenu(code, code, MenuType.MENU, null, routePath, null, null, 0, true, null);
    }

    private static ManifestPermission permission(String code, RiskLevel riskLevel) {
        return new ManifestPermission(code, code, riskLevel);
    }

    /** 名称已在管理台改过，用于证明显示属性不参与差异比对。 */
    private static Menu activeMenu(String id, String code, String routePath) {
        return new Menu(id, APP_ID, code, "管理台改过的名称", MenuType.MENU.name(), null, routePath, null, null, 0,
                true, null, LifecycleStatus.ACTIVE);
    }

    private static PermissionCode activePermission(String id, String code, RiskLevel riskLevel) {
        return new PermissionCode(id, APP_ID, code, code, riskLevel, LifecycleStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"), null);
    }
}
