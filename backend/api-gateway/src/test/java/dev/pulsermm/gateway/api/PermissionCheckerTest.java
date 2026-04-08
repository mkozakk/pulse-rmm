package dev.pulsermm.gateway.api;

import dev.pulsermm.common.rbac.PermissionChecker;
import dev.pulsermm.common.rbac.ResolvedPermission;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionCheckerTest {

    @Test
    void globalPermissionMatchesAnyGroup() {
        var perms = List.of(new ResolvedPermission("remote:shell", null));
        assertThat(PermissionChecker.hasPermission(perms, "remote:shell", UUID.randomUUID())).isTrue();
    }

    @Test
    void scopedPermissionMatchesCorrectGroup() {
        UUID groupId = UUID.randomUUID();
        var perms = List.of(new ResolvedPermission("remote:shell", groupId));
        assertThat(PermissionChecker.hasPermission(perms, "remote:shell", groupId)).isTrue();
    }

    @Test
    void scopedPermissionDoesNotMatchDifferentGroup() {
        UUID groupA = UUID.randomUUID();
        UUID groupB = UUID.randomUUID();
        var perms = List.of(new ResolvedPermission("remote:shell", groupA));
        assertThat(PermissionChecker.hasPermission(perms, "remote:shell", groupB)).isFalse();
    }

    @Test
    void emptyPermissionsReturnsFalse() {
        assertThat(PermissionChecker.hasPermission(List.of(), "remote:shell", null)).isFalse();
    }

    @Test
    void missingPermissionReturnsFalse() {
        var perms = List.of(new ResolvedPermission("endpoint:view", null));
        assertThat(PermissionChecker.hasPermission(perms, "remote:shell", null)).isFalse();
    }
}
