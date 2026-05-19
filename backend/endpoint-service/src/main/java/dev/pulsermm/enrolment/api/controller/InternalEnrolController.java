package dev.pulsermm.enrolment.api.controller;

import dev.pulsermm.enrolment.api.errors.InvalidTokenException;
import dev.pulsermm.enrolment.application.EnrolService;
import dev.pulsermm.enrolment.infrastructure.EndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    public InternalEnrolController(EnrolService enrolService, EndpointRepository endpointRepository) {
        this.enrolService = enrolService;
        this.endpointRepository = endpointRepository;
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
