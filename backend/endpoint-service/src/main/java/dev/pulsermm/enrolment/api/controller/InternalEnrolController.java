package dev.pulsermm.enrolment.api.controller;

import dev.pulsermm.enrolment.api.errors.InvalidTokenException;
import dev.pulsermm.enrolment.application.EnrolService;
import dev.pulsermm.enrolment.infrastructure.EndpointRepository;
import dev.pulsermm.enrolment.infrastructure.GroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@RestController
@RequestMapping("/internal")
public class InternalEnrolController {

    private static final Logger logger = LoggerFactory.getLogger(InternalEnrolController.class);

    private final EnrolService enrolService;
    private final EndpointRepository endpointRepository;
    private final GroupRepository groupRepository;
    private final String internalSecret;

    public InternalEnrolController(EnrolService enrolService,
                                   EndpointRepository endpointRepository,
                                   GroupRepository groupRepository,
                                   @Value("${pulse.identity.internal-secret}") String internalSecret) {
        this.enrolService = enrolService;
        this.endpointRepository = endpointRepository;
        this.groupRepository = groupRepository;
        this.internalSecret = internalSecret;
    }

    // Resolves an endpoint's owning org (endpoint -> group -> org) for the gateway's chain validation.
    // 204 = no org (global/unassigned group) or endpoint unknown.
    @GetMapping("/endpoints/{id}/org")
    public ResponseEntity<UUID> endpointOrg(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!internalSecret.equals(token)) {
            return ResponseEntity.status(403).build();
        }
        UUID orgId = endpointRepository.findById(id)
            .map(e -> groupRepository.findById(e.getGroupId()).map(g -> g.getOrgId()).orElse(null))
            .orElse(null);
        return orgId == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(orgId);
    }

    record EnrolRequest(String token, String publicKey, String hostname, String os, String arch, String csrPem) {}
    record EnrolResponse(String endpointId, String certPem, String caBundlePem) {}

    @PostMapping("/enrol")
    public ResponseEntity<?> enrol(@RequestBody EnrolRequest req) {
        try {
            UUID tokenId = UUID.fromString(req.token());
            byte[] publicKey = Base64.getDecoder().decode(req.publicKey());

            var result = enrolService.enrolWithCsr(tokenId, publicKey, req.hostname(), req.os(), req.arch(), req.csrPem());

            return ResponseEntity.ok(new EnrolResponse(result.endpointId().toString(), result.certPem(), result.caBundlePem()));
        } catch (InvalidTokenException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid token format");
        } catch (Exception e) {
            logger.error("Internal enrol failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("enrol failed");
        }
    }

    record HeartbeatRequest(String endpointId) {}

    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@RequestBody HeartbeatRequest req) {
        try {
            UUID id = UUID.fromString(req.endpointId());
            endpointRepository.findById(id).ifPresent(endpoint -> {
                endpoint.setLastSeen(Instant.now());
                endpointRepository.save(endpoint);
            });
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Internal heartbeat failed for {}", req.endpointId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
