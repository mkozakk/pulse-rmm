package dev.pulsermm.rbac.application;

import dev.pulsermm.rbac.domain.Permission;
import dev.pulsermm.rbac.domain.Role;
import dev.pulsermm.rbac.domain.RolePermission;
import dev.pulsermm.rbac.domain.RolePermissionId;
import dev.pulsermm.rbac.domain.UserRole;
import dev.pulsermm.rbac.domain.UserRoleId;
import dev.pulsermm.rbac.domain.UserPermission;
import dev.pulsermm.rbac.domain.UserPermissionId;
import dev.pulsermm.rbac.infrastructure.PermissionRepository;
import dev.pulsermm.rbac.infrastructure.RolePermissionRepository;
import dev.pulsermm.rbac.infrastructure.RoleRepository;
import dev.pulsermm.rbac.infrastructure.UserPermissionRepository;
import dev.pulsermm.rbac.infrastructure.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RbacService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final PermissionEvaluationService permissionEvaluationService;

    public RbacService(RoleRepository roleRepository,
                      PermissionRepository permissionRepository,
                      RolePermissionRepository rolePermissionRepository,
                      UserRoleRepository userRoleRepository,
                      UserPermissionRepository userPermissionRepository,
                      PermissionEvaluationService permissionEvaluationService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.userPermissionRepository = userPermissionRepository;
        this.permissionEvaluationService = permissionEvaluationService;
    }

    @Transactional
    public Role createRole(String name) {
        return roleRepository.save(new Role(name));
    }

    @Transactional
    public void addPermissionToRole(UUID roleId, UUID permissionId, UUID groupScopeId) {
        rolePermissionRepository.save(new RolePermission(roleId, permissionId, groupScopeId));
    }

    @Transactional
    public void assignRoleToUser(UUID userId, UUID roleId) {
        userRoleRepository.save(new UserRole(new UserRoleId(userId, roleId)));
    }

    @Transactional
    public void grantDirectPermission(UUID userId, UUID permissionId, UUID groupScopeId, OffsetDateTime expiresAt) {
        userPermissionRepository.save(new UserPermission(userId, permissionId, groupScopeId, expiresAt));
        permissionEvaluationService.invalidate(userId);
    }

    @Transactional
    public void revokeDirectPermission(UUID userId, UUID permissionId) {
        userPermissionRepository.deleteById(new UserPermissionId(userId, permissionId));
        permissionEvaluationService.invalidate(userId);
    }

    @Transactional
    public void removeRoleFromUser(UUID userId, UUID roleId) {
        userRoleRepository.deleteById(new UserRoleId(userId, roleId));
        permissionEvaluationService.invalidate(userId);
    }

    @Transactional(readOnly = true)
    public List<String> getUserRoles(UUID userId) {
        return userRoleRepository.findAllByIdUserId(userId).stream()
            .map(ur -> roleRepository.findById(ur.getId().getRoleId()))
            .filter(java.util.Optional::isPresent)
            .map(opt -> opt.get().getName())
            .toList();
    }

    @Transactional
    public void replaceUserRoles(UUID userId, List<UUID> roleIds) {
        userRoleRepository.deleteAllByIdUserId(userId);
        for (UUID roleId : roleIds) {
            userRoleRepository.save(new UserRole(new UserRoleId(userId, roleId)));
        }
        permissionEvaluationService.invalidate(userId);
    }

    @Transactional
    public void removeAllUserData(UUID userId) {
        userRoleRepository.deleteAllByIdUserId(userId);
        userPermissionRepository.deleteAllByIdUserId(userId);
        permissionEvaluationService.invalidate(userId);
    }
}
