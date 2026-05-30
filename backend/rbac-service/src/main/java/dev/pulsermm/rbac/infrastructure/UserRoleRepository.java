package dev.pulsermm.rbac.infrastructure;

import dev.pulsermm.rbac.domain.UserRole;
import dev.pulsermm.rbac.domain.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    List<UserRole> findAllByIdUserId(UUID userId);
    void deleteAllByIdUserId(UUID userId);
}
