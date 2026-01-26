package dev.pulsermm.identity.api;

import dev.pulsermm.identity.api.dto.CreateRoleRequest;
import dev.pulsermm.identity.application.PermissionEvaluationService;
import dev.pulsermm.identity.application.RbacService;
import dev.pulsermm.identity.domain.Permission;
import dev.pulsermm.identity.domain.Role;
import dev.pulsermm.identity.infrastructure.PermissionRepository;
import dev.pulsermm.identity.infrastructure.RoleRepository;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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

    @GetMapping("/permissions")
    public ResponseEntity<List<Permission>> listPermissions() {
        return ResponseEntity.ok(permissionRepository.findAll());
    }

    @GetMapping("/roles")
    public ResponseEntity<List<Role>> listRoles() {
        return ResponseEntity.ok(roleRepository.findAll());
    }

    @PostMapping("/roles")
    public ResponseEntity<Role> createRole(@RequestBody CreateRoleRequest request) {
        Role role = rbacService.createRole(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(role);
    }
}
