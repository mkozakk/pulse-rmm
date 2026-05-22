package dev.pulsermm.enrolment.application;

import dev.pulsermm.enrolment.infrastructure.CaClient;
import dev.pulsermm.enrolment.infrastructure.EndpointRepository;
import dev.pulsermm.enrolment.infrastructure.EndpointRevocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CertRenewService {

    private final EndpointRepository endpointRepository;
    private final EndpointRevocationRepository revocationRepository;
    private final CaClient caClient;

    public CertRenewService(EndpointRepository endpointRepository,
                            EndpointRevocationRepository revocationRepository,
                            @Autowired(required = false) CaClient caClient) {
        this.endpointRepository = endpointRepository;
        this.revocationRepository = revocationRepository;
        this.caClient = caClient;
    }

    public record RenewResult(String certPem, String caBundlePem) {}

    public static class RevokedEndpointException extends RuntimeException {
        public RevokedEndpointException(UUID id) { super("endpoint " + id + " is revoked"); }
    }

    public static class UnknownEndpointException extends RuntimeException {
        public UnknownEndpointException(UUID id) { super("endpoint " + id + " not found"); }
    }

    public RenewResult renew(UUID endpointId, String csrPem) {
        if (caClient == null) {
            throw new IllegalStateException("ca-service is disabled (pulse.ca.enabled=false)");
        }
        if (csrPem == null || csrPem.isBlank()) {
            throw new IllegalArgumentException("csrPem is required");
        }
        if (endpointRepository.findById(endpointId).isEmpty()) {
            throw new UnknownEndpointException(endpointId);
        }
        if (revocationRepository.existsByEndpointId(endpointId)) {
            throw new RevokedEndpointException(endpointId);
        }
        var signed = caClient.sign(csrPem, endpointId);
        return new RenewResult(signed.certPem(), signed.caBundlePem());
    }
}
