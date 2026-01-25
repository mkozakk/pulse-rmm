package dev.pulsermm.identity.application;

import dev.pulsermm.identity.api.dto.ResolvedPermission;
import dev.pulsermm.identity.domain.UserPermission;
import dev.pulsermm.identity.infrastructure.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class PermissionEvaluationService {

    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final PermissionRepository permissionRepository;

    public PermissionEvaluationService(UserRoleRepository userRoleRepository,
                                       RolePermissionRepository rolePermissionRepository,
                                       UserPermissionRepository userPermissionRepository,
                                       PermissionRepository permissionRepository) {
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userPermissionRepository = userPermissionRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public Set<ResolvedPermission> resolve(UUID userId) {
        Set<ResolvedPermission> result = new HashSet<>();

        for (var userRole : userRoleRepository.findAllByIdUserId(userId)) {
            for (var rp : rolePermissionRepository.findAllByIdRoleId(userRole.getId().getRoleId())) {
                var perm = permissionRepository.findById(rp.getId().getPermissionId()).orElseThrow();
                result.add(new ResolvedPermission(perm.getName(), rp.getGroupScopeId()));
            }
        }

        for (UserPermission up : userPermissionRepository.findAllByIdUserId(userId)) {
            if (up.getExpiresAt() != null && up.getExpiresAt().isBefore(OffsetDateTime.now())) {
                continue;
            }
            var perm = permissionRepository.findById(up.getId().getPermissionId()).orElseThrow();
            result.add(new ResolvedPermission(perm.getName(), up.getGroupScopeId()));
        }

        return result;
    }

    // invalidate is a no-op until Phase 3 adds Redis caching
    public void invalidate(UUID userId) {}
}
