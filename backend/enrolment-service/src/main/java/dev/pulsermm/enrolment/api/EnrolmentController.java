package dev.pulsermm.enrolment.api;

import dev.pulsermm.enrolment.application.MoveEndpointService;
import dev.pulsermm.enrolment.application.TagService;
import dev.pulsermm.enrolment.application.TokenService;
import dev.pulsermm.enrolment.domain.Endpoint;
import dev.pulsermm.enrolment.infrastructure.EndpointRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
public class EnrolmentController {
    private final TokenService tokenService;
    private final EndpointRepository endpointRepository;
    private final MoveEndpointService moveEndpointService;
    private final TagService tagService;
    private final JdbcTemplate jdbcTemplate;

    public EnrolmentController(TokenService tokenService,
                                EndpointRepository endpointRepository,
                                MoveEndpointService moveEndpointService,
                                TagService tagService,
                                JdbcTemplate jdbcTemplate) {
        this.tokenService = tokenService;
        this.endpointRepository = endpointRepository;
        this.moveEndpointService = moveEndpointService;
        this.tagService = tagService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/api/enrolment/tokens")
    public ResponseEntity<TokenResponse> createToken(
            @Valid @RequestBody CreateTokenRequest request,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var token = tokenService.createToken(request.groupId(), request.ttlHours());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new TokenResponse(token.getId(), token.getExpiresAt()));
    }

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
