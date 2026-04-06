package dev.pulsermm.rbac.api;

import dev.pulsermm.rbac.api.dto.CreateOrganizationRequest;
import dev.pulsermm.rbac.api.dto.CreateUserRequest;
import dev.pulsermm.rbac.api.dto.OrganizationResponse;
import dev.pulsermm.rbac.api.dto.UpdateOrganizationRequest;
import dev.pulsermm.rbac.api.dto.UserResponse;
import dev.pulsermm.rbac.application.OrganizationService;
import dev.pulsermm.rbac.application.RbacService;
import dev.pulsermm.rbac.domain.Organization;
import dev.pulsermm.rbac.infrastructure.RoleRepository;
import dev.pulsermm.rbac.infrastructure.keycloak.KeycloakAdminClient;
import dev.pulsermm.rbac.infrastructure.keycloak.KeycloakUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(name = "Organizations", description = "Tenant organization management (global admin only)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final RbacService rbacService;
    private final RoleRepository roleRepository;

    public OrganizationController(OrganizationService organizationService,
                                  KeycloakAdminClient keycloakAdminClient,
                                  RbacService rbacService,
                                  RoleRepository roleRepository) {
        this.organizationService = organizationService;
        this.keycloakAdminClient = keycloakAdminClient;
        this.rbacService = rbacService;
        this.roleRepository = roleRepository;
    }

    @Operation(summary = "Create an organization")
    @PostMapping
    public ResponseEntity<OrganizationResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                       @RequestBody @Valid CreateOrganizationRequest request) {
        requireGlobalAdmin(jwt);
        Organization org = organizationService.create(request.name());
        return ResponseEntity.created(URI.create("/api/organizations/" + org.getId())).body(toResponse(org));
    }

    @Operation(summary = "List all organizations")
    @GetMapping
    public ResponseEntity<List<OrganizationResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        requireGlobalAdmin(jwt);
        return ResponseEntity.ok(organizationService.list().stream().map(this::toResponse).toList());
    }

    @Operation(summary = "Get an organization")
    @GetMapping("/{id}")
    public ResponseEntity<OrganizationResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        requireGlobalAdmin(jwt);
        return ResponseEntity.ok(toResponse(organizationService.get(id)));
    }

    @Operation(summary = "Update an organization")
    @PutMapping("/{id}")
    public ResponseEntity<OrganizationResponse> update(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable UUID id,
                                                       @RequestBody @Valid UpdateOrganizationRequest request) {
        requireGlobalAdmin(jwt);
        return ResponseEntity.ok(toResponse(organizationService.update(id, request.name())));
    }

    @Operation(summary = "Delete an organization (only when empty)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        requireGlobalAdmin(jwt);
        organizationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Create a user in an organization")
    @PostMapping("/{orgId}/users")
    public ResponseEntity<UserResponse> createUser(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable UUID orgId,
                                                   @RequestBody @Valid CreateUserRequest request) {
        requireGlobalAdmin(jwt);
        organizationService.get(orgId);

        UUID userId = keycloakAdminClient.createUser(
            request.username(), request.email(), request.firstName(), request.lastName(), request.password(), orgId);

        if (request.roleName() != null) {
            roleRepository.findByName(request.roleName())
                .ifPresent(role -> rbacService.assignRoleToUser(userId, role.getId()));
        }

        var user = keycloakAdminClient.getUser(userId);
        return ResponseEntity.created(URI.create("/api/organizations/" + orgId + "/users/" + userId))
            .body(toResponse(user));
    }

    @Operation(summary = "List users in an organization")
    @GetMapping("/{orgId}/users")
    public ResponseEntity<List<UserResponse>> listUsers(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orgId) {
        requireGlobalAdmin(jwt);
        organizationService.get(orgId);
        return ResponseEntity.ok(keycloakAdminClient.listUsersByOrg(orgId).stream().map(this::toResponse).toList());
    }

    // Global admin = no org_id claim on the JWT. Org-scoped users cannot touch organization APIs.
    private void requireGlobalAdmin(Jwt jwt) {
        String orgId = jwt.getClaimAsString("org_id");
        if (orgId != null && !orgId.isBlank()) {
            throw new ForbiddenException("Global admin only");
        }
    }

    private OrganizationResponse toResponse(Organization org) {
        return new OrganizationResponse(org.getId(), org.getName(), org.getCreatedAt());
    }

    private UserResponse toResponse(KeycloakUser user) {
        var roles = rbacService.getUserRoles(user.id());
        return new UserResponse(user.id(), user.username(), user.email(),
            user.firstName(), user.lastName(), user.enabled(), roles);
    }
}
