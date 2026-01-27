package dev.pulsermm.identity.api;

import dev.pulsermm.identity.api.dto.ResolvedPermission;
import dev.pulsermm.identity.application.PermissionEvaluationService;
import dev.pulsermm.identity.domain.EndpointGroupMembership;
import dev.pulsermm.identity.infrastructure.EndpointGroupMembershipRepository;
import dev.pulsermm.identity.infrastructure.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/rbac")
public class InternalRbacController {

    private final PermissionEvaluationService evaluationService;
    private final UserRepository userRepository;
    private final EndpointGroupMembershipRepository membershipRepository;
    private final String internalSecret;

    public InternalRbacController(PermissionEvaluationService evaluationService,
                                   UserRepository userRepository,
                                   EndpointGroupMembershipRepository membershipRepository,
                                   @Value("${pulse.internal.secret}") String internalSecret) {
        this.evaluationService = evaluationService;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.internalSecret = internalSecret;
    }

    @GetMapping("/endpoint-groups/{endpointId}")
    public ResponseEntity<UUID> getEndpointGroup(
            @PathVariable UUID endpointId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!internalSecret.equals(token)) {
            return ResponseEntity.status(403).build();
        }
        return membershipRepository.findByEndpointId(endpointId)
            .map(m -> ResponseEntity.ok(m.getGroupId()))
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/endpoint-groups/{endpointId}")
    public ResponseEntity<Void> setEndpointGroup(
            @PathVariable UUID endpointId,
            @RequestBody UUID groupId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!internalSecret.equals(token)) {
            return ResponseEntity.status(403).build();
        }
        membershipRepository.save(new EndpointGroupMembership(endpointId, groupId));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/permissions/{userId}")
    public ResponseEntity<List<ResolvedPermission>> getPermissions(
            @PathVariable UUID userId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {

        if (!internalSecret.equals(token)) {
            return ResponseEntity.status(403).build();
        }

        if (!userRepository.existsById(userId)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(List.copyOf(evaluationService.resolve(userId)));
    }
}
