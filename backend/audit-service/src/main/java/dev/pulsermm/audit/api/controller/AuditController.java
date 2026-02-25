package dev.pulsermm.audit.api.controller;

import dev.pulsermm.audit.api.dto.AuditEventResponse;
import dev.pulsermm.audit.application.AuditQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditQueryService queryService;

    public AuditController(AuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public Page<AuditEventResponse> list(
            @RequestParam(required = false) UUID user,
            @RequestParam(required = false) UUID endpoint,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int cappedSize = Math.min(size, 200);
        return queryService.list(user, endpoint, from, to, PageRequest.of(page, cappedSize))
                .map(AuditEventResponse::from);
    }

    @DeleteMapping("/**")
    public ResponseEntity<Void> deleteNotAllowed() {
        return ResponseEntity.status(403).build();
    }
}
