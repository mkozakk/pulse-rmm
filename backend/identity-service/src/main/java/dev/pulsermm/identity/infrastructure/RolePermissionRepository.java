package dev.pulsermm.identity.infrastructure;

import dev.pulsermm.identity.domain.RolePermission;
import dev.pulsermm.identity.domain.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
    List<RolePermission> findAllByIdRoleId(UUID roleId);
}
