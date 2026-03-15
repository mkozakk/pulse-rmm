package dev.pulsermm.enrolment.api.controller;

import dev.pulsermm.enrolment.application.CertRenewService;
import dev.pulsermm.enrolment.application.CertRenewService.RevokedEndpointException;
import dev.pulsermm.enrolment.application.CertRenewService.UnknownEndpointException;
import dev.pulsermm.enrolment.infrastructure.EndpointRevocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal")
public class InternalCertController {

    private static final Logger logger = LoggerFactory.getLogger(InternalCertController.class);

    private final CertRenewService renewService;
    private final EndpointRevocationRepository revocationRepository;

    public InternalCertController(CertRenewService renewService,
                                  EndpointRevocationRepository revocationRepository) {
        this.renewService = renewService;
        this.revocationRepository = revocationRepository;
    }

    record RenewRequest(String endpointId, String csrPem) {}
    record RenewResponse(String certPem, String caBundlePem) {}
    record RevokedStatus(String endpointId, boolean revoked) {}

    @PostMapping("/cert/renew")
    public ResponseEntity<?> renew(@RequestBody RenewRequest req) {
        try {
            UUID id = UUID.fromString(req.endpointId());
            var result = renewService.renew(id, req.csrPem());
            return ResponseEntity.ok(new RenewResponse(result.certPem(), result.caBundlePem()));
        } catch (RevokedEndpointException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (UnknownEndpointException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Cert renew failed for {}", req.endpointId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "renew failed"));
        }
    }

    @GetMapping("/endpoints/{id}/revoked")
    public ResponseEntity<RevokedStatus> isRevoked(@PathVariable("id") String id) {
        try {
            UUID uuid = UUID.fromString(id);
            boolean revoked = revocationRepository.existsByEndpointId(uuid);
            return ResponseEntity.ok(new RevokedStatus(id, revoked));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
