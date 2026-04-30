package dev.pulsermm.identity.infrastructure;

import dev.pulsermm.identity.domain.UserPermission;
import dev.pulsermm.identity.domain.UserPermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface UserPermissionRepository extends JpaRepository<UserPermission, UserPermissionId> {
    List<UserPermission> findAllByIdUserId(UUID userId);
}
