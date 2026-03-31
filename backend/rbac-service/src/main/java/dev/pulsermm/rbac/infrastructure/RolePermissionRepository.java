package dev.pulsermm.rbac.infrastructure;

import dev.pulsermm.rbac.domain.RolePermission;
import dev.pulsermm.rbac.domain.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
    List<RolePermission> findAllByIdRoleId(UUID roleId);
}
