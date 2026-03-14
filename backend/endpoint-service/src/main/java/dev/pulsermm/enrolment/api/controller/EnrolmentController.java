package dev.pulsermm.enrolment.api.controller;

import dev.pulsermm.enrolment.api.dto.CreateTokenRequest;
import dev.pulsermm.enrolment.api.dto.EndpointResponse;
import dev.pulsermm.enrolment.api.dto.MoveEndpointRequest;
import dev.pulsermm.enrolment.api.dto.SetTagsRequest;
import dev.pulsermm.enrolment.api.dto.TokenResponse;
import dev.pulsermm.enrolment.application.MoveEndpointService;
import dev.pulsermm.enrolment.application.TagService;
import dev.pulsermm.enrolment.application.TokenService;
import dev.pulsermm.enrolment.domain.Endpoint;
import dev.pulsermm.enrolment.domain.EndpointRevocation;
import dev.pulsermm.enrolment.infrastructure.EndpointRepository;
import dev.pulsermm.enrolment.infrastructure.EndpointRevocationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Tag(name = "Enrolment", description = "Enrolment tokens and endpoint inventory")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class EnrolmentController {
    @Value("${pulse.api.url:http://localhost:8080}")
    private String apiUrl;

    private final TokenService tokenService;
    private final EndpointRepository endpointRepository;
    private final MoveEndpointService moveEndpointService;
    private final TagService tagService;
    private final JdbcTemplate jdbcTemplate;
    private final EndpointRevocationRepository revocationRepository;

    public EnrolmentController(TokenService tokenService,
                                EndpointRepository endpointRepository,
                                MoveEndpointService moveEndpointService,
                                TagService tagService,
                                JdbcTemplate jdbcTemplate,
                                EndpointRevocationRepository revocationRepository) {
        this.tokenService = tokenService;
        this.endpointRepository = endpointRepository;
        this.moveEndpointService = moveEndpointService;
        this.tagService = tagService;
        this.jdbcTemplate = jdbcTemplate;
        this.revocationRepository = revocationRepository;
    }

    record RevokeRequest(String reason) {}

    @Operation(summary = "Revoke endpoint", description = "Marks endpoint as revoked; further cert renewals will be denied.")
    @ApiResponse(responseCode = "204", description = "Endpoint revoked")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "404", description = "Endpoint not found")
    @PostMapping("/api/endpoints/{id}/revoke")
    public ResponseEntity<Void> revokeEndpoint(
            @PathVariable UUID id,
            @RequestBody(required = false) RevokeRequest request,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (endpointRepository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String reason = request != null ? request.reason() : null;
        revocationRepository.save(new EndpointRevocation(id, java.time.Instant.now(), reason));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Create enrolment token")
    @ApiResponse(responseCode = "201", description = "Token created",
        content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @PostMapping("/api/enrolment/tokens")
    public ResponseEntity<TokenResponse> createToken(
            @Valid @RequestBody CreateTokenRequest request,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var token = tokenService.createToken(request.groupId(), request.ttlHours());
        String id = token.getId().toString();
        String installSh = "curl -fsSL " + apiUrl + "/install/" + id + ".sh | sudo bash";
        String installPs1 = "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -ExecutionPolicy Bypass -Command \"iex (iwr -Uri ''" + apiUrl + "/install/" + id + ".ps1'' -UseBasicParsing).Content\"'";
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new TokenResponse(token.getId(), token.getExpiresAt(), installSh, installPs1));
    }

    @Operation(summary = "List endpoints",
        description = "Optional tag filtering using repeated query param: ?tag=key=value")
    @ApiResponse(responseCode = "200", description = "Endpoint list",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = EndpointResponse.class))))
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @GetMapping("/api/endpoints")
    public ResponseEntity<List<EndpointResponse>> listEndpoints(
            @RequestParam(required = false) List<String> tag,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Endpoint> endpoints = (tag == null || tag.isEmpty())
            ? endpointRepository.findAll()
            : filterByTags(tag);

        return ResponseEntity.ok(endpoints.stream().map(EndpointResponse::from).toList());
    }

    @Operation(summary = "Get endpoint details")
    @ApiResponse(responseCode = "200", description = "Endpoint details",
        content = @Content(schema = @Schema(implementation = EndpointResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "404", description = "Endpoint not found")
    @GetMapping("/api/endpoints/{id}")
    public ResponseEntity<EndpointResponse> getEndpoint(
            @PathVariable UUID id,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return endpointRepository.findById(id)
            .map(EndpointResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    private List<Endpoint> filterByTags(List<String> tags) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, hostname, os, arch, group_id, public_key, enrolled_at, last_seen FROM enrolment.endpoints e WHERE 1=1");
        List<Object> params = new ArrayList<>();
        for (String tag : tags) {
            String[] parts = tag.split("=", 2);
            sql.append(" AND EXISTS (SELECT 1 FROM enrolment.endpoint_tags t WHERE t.endpoint_id = e.id AND t.key = ? AND t.value = ?)");
            params.add(parts[0]);
            params.add(parts[1]);
        }
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Endpoint e = new Endpoint();
            e.setId(rs.getObject("id", UUID.class));
            e.setHostname(rs.getString("hostname"));
            e.setOs(rs.getString("os"));
            e.setArch(rs.getString("arch"));
            e.setGroupId(rs.getObject("group_id", UUID.class));
            e.setPublicKey(rs.getBytes("public_key"));
            e.setEnrolledAt(rs.getTimestamp("enrolled_at").toInstant());
            e.setLastSeen(rs.getTimestamp("last_seen").toInstant());
            return e;
        }, params.toArray());
    }

    @Operation(summary = "Set endpoint tags")
    @ApiResponse(responseCode = "200", description = "Tags updated")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @PutMapping("/api/endpoints/{id}/tags")
    public ResponseEntity<Void> setTags(
            @PathVariable UUID id,
            @RequestBody SetTagsRequest request,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        tagService.setTags(id, request.tags());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Move endpoint to group")
    @ApiResponse(responseCode = "200", description = "Endpoint moved")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @PutMapping("/api/endpoints/{id}/group")
    public ResponseEntity<Void> moveEndpoint(
            @PathVariable UUID id,
            @RequestBody MoveEndpointRequest request,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        moveEndpointService.move(id, request.groupId());
        return ResponseEntity.ok().build();
    }
}
