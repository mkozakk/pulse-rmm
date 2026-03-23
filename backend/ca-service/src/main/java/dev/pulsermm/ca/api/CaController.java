package dev.pulsermm.ca.api;

import dev.pulsermm.ca.application.CaService;
import dev.pulsermm.ca.application.InvalidCsrException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Tag(name = "CA", description = "Internal certificate authority — endpoint CSR signing and CA bundle retrieval")
@RestController
@RequestMapping("/internal/ca")
public class CaController {

    private final CaService caService;
    private final Duration defaultTtl;

    public CaController(CaService caService,
                        @org.springframework.beans.factory.annotation.Value("${pulse.ca.cert-ttl}") Duration defaultTtl) {
        this.caService = caService;
        this.defaultTtl = defaultTtl;
    }

    public record SignRequest(String csrPem, UUID endpointId) {}
    public record SignResponse(String certPem, String caBundlePem) {}
    public record SignServerRequest(String csrPem, String commonName, List<String> sanDnsNames) {}

    @Operation(summary = "Sign an endpoint CSR", description = "Signs a CSR for an enrolled endpoint and returns the signed certificate plus CA bundle.")
    @ApiResponse(responseCode = "200", description = "Certificate signed")
    @ApiResponse(responseCode = "400", description = "Invalid CSR")
    @PostMapping("/sign")
    public ResponseEntity<SignResponse> sign(@RequestBody SignRequest req) {
        String cert = caService.signCsr(req.csrPem(), req.endpointId(), defaultTtl);
        return ResponseEntity.ok(new SignResponse(cert, caService.getCaBundle()));
    }

    @Operation(summary = "Sign a server CSR", description = "Signs a CSR for an internal service certificate with optional SAN DNS names.")
    @ApiResponse(responseCode = "200", description = "Certificate signed")
    @ApiResponse(responseCode = "400", description = "Invalid CSR")
    @PostMapping("/server-certs")
    public ResponseEntity<SignResponse> signServer(@RequestBody SignServerRequest req) {
        List<String> sans = req.sanDnsNames() == null ? List.of() : req.sanDnsNames();
        String cert = caService.signServerCsr(req.csrPem(), req.commonName(), sans, defaultTtl);
        return ResponseEntity.ok(new SignResponse(cert, caService.getCaBundle()));
    }

    @Operation(summary = "Get CA bundle", description = "Returns the PEM-encoded CA certificate bundle for trust configuration.")
    @ApiResponse(responseCode = "200", description = "CA bundle returned")
    @GetMapping("/bundle")
    public ResponseEntity<String> bundle() {
        return ResponseEntity.ok(caService.getCaBundle());
    }

    @ExceptionHandler(InvalidCsrException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCsr(InvalidCsrException e) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Invalid CSR");
        pd.setDetail(e.getMessage());
        return ResponseEntity.badRequest().body(pd);
    }
}
