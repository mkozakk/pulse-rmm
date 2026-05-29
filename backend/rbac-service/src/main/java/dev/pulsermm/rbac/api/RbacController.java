package dev.pulsermm.rbac.api;

import dev.pulsermm.rbac.api.dto.CreateRoleRequest;
import dev.pulsermm.rbac.application.PermissionEvaluationService;
import dev.pulsermm.rbac.application.RbacService;
import dev.pulsermm.rbac.domain.Permission;
import dev.pulsermm.rbac.domain.Role;
import dev.pulsermm.rbac.infrastructure.PermissionRepository;
import dev.pulsermm.rbac.infrastructure.RoleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "RBAC", description = "Roles and permissions management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/identity/rbac")
public class RbacController {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RbacService rbacService;
    private final PermissionEvaluationService permissionEvaluationService;

    public RbacController(PermissionRepository permissionRepository,
                        RoleRepository roleRepository,
                        RbacService rbacService,
                        PermissionEvaluationService permissionEvaluationService) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.rbacService = rbacService;
        this.permissionEvaluationService = permissionEvaluationService;
    }

    @Operation(summary = "List all permissions")
    @ApiResponse(responseCode = "200", description = "Permission list")
    @GetMapping("/permissions")
    public ResponseEntity<List<Permission>> listPermissions() {
        return ResponseEntity.ok(permissionRepository.findAll());
    }

    @Operation(summary = "List all roles")
    @ApiResponse(responseCode = "200", description = "Role list")
    @GetMapping("/roles")
    public ResponseEntity<List<Role>> listRoles() {
        return ResponseEntity.ok(roleRepository.findAll());
    }

    @Operation(summary = "Create a new role")
    @ApiResponse(responseCode = "201", description = "Role created")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "409", description = "Role name already exists")
    @PostMapping("/roles")
    public ResponseEntity<Role> createRole(@RequestBody CreateRoleRequest request) {
        Role role = rbacService.createRole(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(role);
    }
}
