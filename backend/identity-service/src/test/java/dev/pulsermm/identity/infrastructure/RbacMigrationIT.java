package dev.pulsermm.identity.infrastructure;

import dev.pulsermm.identity.domain.Role;
import dev.pulsermm.identity.domain.Permission;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Disabled("Java 21 incompatibility with DataJpaTest context loading")
class RbacMigrationIT {

    @Autowired PermissionRepository permissionRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired RolePermissionRepository rolePermissionRepository;

    @Test
    void permissionCatalogIsSeeded() {
        assertThat(permissionRepository.findByName("endpoint:view")).isPresent();
        assertThat(permissionRepository.findByName("remote:shell")).isPresent();
        assertThat(permissionRepository.findByName("identity:rbac:manage")).isPresent();
    }

    @Test
    void defaultRolesExist() {
        assertThat(roleRepository.findByName("Admin")).isPresent();
        assertThat(roleRepository.findByName("Senior Technician")).isPresent();
        assertThat(roleRepository.findByName("Junior Technician")).isPresent();
        assertThat(roleRepository.findByName("Auditor")).isPresent();
    }

    @Test
    void adminRoleHasAllPermissions() {
        Role admin = roleRepository.findByName("Admin").orElseThrow();
        var perms = rolePermissionRepository.findAllByIdRoleId(admin.getId());
        assertThat(perms).hasSizeGreaterThanOrEqualTo(30);
    }

    @Test
    void juniorTechnicianDoesNotHaveRemoteShell() {
        Role junior = roleRepository.findByName("Junior Technician").orElseThrow();
        Permission shell = permissionRepository.findByName("remote:shell").orElseThrow();
        var perms = rolePermissionRepository.findAllByIdRoleId(junior.getId());
        assertThat(perms).noneMatch(rp -> rp.getId().getPermissionId().equals(shell.getId()));
    }

    @Test
    void seniorTechnicianHasRemoteShell() {
        Role senior = roleRepository.findByName("Senior Technician").orElseThrow();
        Permission shell = permissionRepository.findByName("remote:shell").orElseThrow();
        var perms = rolePermissionRepository.findAllByIdRoleId(senior.getId());
        assertThat(perms).anyMatch(rp -> rp.getId().getPermissionId().equals(shell.getId()));
    }
}
