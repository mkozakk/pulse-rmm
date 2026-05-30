package dev.pulsermm.rbac.infrastructure;

import dev.pulsermm.rbac.domain.UserPermission;
import dev.pulsermm.rbac.domain.UserPermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface UserPermissionRepository extends JpaRepository<UserPermission, UserPermissionId> {
    List<UserPermission> findAllByIdUserId(UUID userId);
    void deleteAllByIdUserId(UUID userId);
}
