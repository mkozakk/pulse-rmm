package dev.pulsermm.identity.infrastructure;

import dev.pulsermm.identity.domain.UserRole;
import dev.pulsermm.identity.domain.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    List<UserRole> findAllByIdUserId(UUID userId);
}
