package dev.pulsermm.rbac.api;

import dev.pulsermm.rbac.api.dto.CreateUserRequest;
import dev.pulsermm.rbac.api.dto.UpdateUserRequest;
import dev.pulsermm.rbac.api.dto.UpdateUserRolesRequest;
import dev.pulsermm.rbac.api.dto.UserResponse;
import dev.pulsermm.rbac.application.PermissionEvaluationService;
import dev.pulsermm.rbac.application.RbacService;
import dev.pulsermm.rbac.infrastructure.RoleRepository;
import dev.pulsermm.rbac.infrastructure.keycloak.KeycloakAdminClient;
import dev.pulsermm.rbac.infrastructure.keycloak.KeycloakUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(name = "Users", description = "User management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/identity/users")
public class UserController {

    private final KeycloakAdminClient keycloakAdminClient;
    private final RbacService rbacService;
    private final RoleRepository roleRepository;
    private final PermissionEvaluationService permissionEvaluationService;

    public UserController(KeycloakAdminClient keycloakAdminClient,
                          RbacService rbacService,
                          RoleRepository roleRepository,
                          PermissionEvaluationService permissionEvaluationService) {
        this.keycloakAdminClient = keycloakAdminClient;
        this.rbacService = rbacService;
        this.roleRepository = roleRepository;
        this.permissionEvaluationService = permissionEvaluationService;
    }

    @Operation(summary = "List all users")
    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers(@AuthenticationPrincipal Jwt jwt) {
        requireManagePermission(jwt);
        UUID org = callerOrg(jwt);
        var users = org == null ? keycloakAdminClient.listUsers() : keycloakAdminClient.listUsersByOrg(org);
        return ResponseEntity.ok(users.stream().map(this::toResponse).toList());
    }

    @Operation(summary = "Get user by id")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        requireManagePermission(jwt);
        var user = requireVisible(callerOrg(jwt), id);
        return ResponseEntity.ok(toResponse(user));
    }

    @Operation(summary = "Create user")
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@AuthenticationPrincipal Jwt jwt,
                                                    @RequestBody @Valid CreateUserRequest request) {
        requireManagePermission(jwt);
        UUID org = callerOrg(jwt);
        // Org admins create users inside their own org and may not mint other org admins.
        if (org != null && "Org Admin".equals(request.roleName())) {
            throw new ForbiddenException();
        }
        var userId = keycloakAdminClient.createUser(
            request.username(), request.email(), request.firstName(), request.lastName(), request.password(), org);

        if (request.roleName() != null) {
            roleRepository.findByName(request.roleName())
                .ifPresent(role -> rbacService.assignRoleToUser(userId, role.getId()));
        }

        var user = keycloakAdminClient.getUser(userId);
        return ResponseEntity.created(URI.create("/api/identity/users/" + userId)).body(toResponse(user));
    }

    @Operation(summary = "Update user")
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateUser(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID id,
                                            @RequestBody UpdateUserRequest request) {
        requireManagePermission(jwt);
        requireVisible(callerOrg(jwt), id);
        keycloakAdminClient.updateUser(id, request.email(), request.firstName(), request.lastName(), request.enabled());
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            keycloakAdminClient.resetPassword(id, request.newPassword());
        }
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Delete user")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        requireManagePermission(jwt);
        requireVisible(callerOrg(jwt), id);
        keycloakAdminClient.deleteUser(id);
        rbacService.removeAllUserData(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Replace user roles")
    @PutMapping("/{id}/roles")
    public ResponseEntity<Void> updateUserRoles(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable UUID id,
                                                 @RequestBody UpdateUserRolesRequest request) {
        requireManagePermission(jwt);
        UUID org = callerOrg(jwt);
        requireVisible(org, id);
        if (org != null && request.roleIds() != null) {
            boolean assigningOrgAdmin = request.roleIds().stream()
                .anyMatch(rid -> roleRepository.findById(rid).map(r -> "Org Admin".equals(r.getName())).orElse(false));
            if (assigningOrgAdmin) {
                throw new ForbiddenException();
            }
        }
        rbacService.replaceUserRoles(id, request.roleIds() != null ? request.roleIds() : List.of());
        return ResponseEntity.ok().build();
    }

    private void requireManagePermission(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        var perms = permissionEvaluationService.resolve(userId);
        boolean hasPermission = perms.stream().anyMatch(p -> "identity:user:manage".equals(p.name()));
        if (!hasPermission) {
            throw new ForbiddenException();
        }
    }

    private UUID callerOrg(Jwt jwt) {
        String orgId = jwt.getClaimAsString("org_id");
        return (orgId == null || orgId.isBlank()) ? null : UUID.fromString(orgId);
    }

    // Org-scoped callers may only touch users in their own org; a user in another org reads as 404 (no leak).
    private KeycloakUser requireVisible(UUID callerOrg, UUID targetId) {
        KeycloakUser user = keycloakAdminClient.getUser(targetId);
        if (callerOrg != null && !callerOrg.equals(user.orgId())) {
            throw new NotFoundException("User not found");
        }
        return user;
    }

    private UserResponse toResponse(KeycloakUser user) {
        var roles = rbacService.getUserRoles(user.id());
        return new UserResponse(user.id(), user.username(), user.email(),
            user.firstName(), user.lastName(), user.enabled(), roles);
    }

    static class ForbiddenException extends RuntimeException {
        ForbiddenException() { super("Forbidden"); }
    }
}
