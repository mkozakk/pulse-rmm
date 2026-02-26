package dev.pulsermm.audit.api.controller;

import dev.pulsermm.audit.api.dto.AuditEventResponse;
import dev.pulsermm.audit.application.AuditExportService;
import dev.pulsermm.audit.application.AuditQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditQueryService queryService;
    private final AuditExportService exportService;

    public AuditController(AuditQueryService queryService, AuditExportService exportService) {
        this.queryService = queryService;
        this.exportService = exportService;
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

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam(required = false) UUID user,
            @RequestParam(required = false) UUID endpoint,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "csv") String format) {

        if ("json".equalsIgnoreCase(format)) {
            StreamingResponseBody body = out -> exportService.streamNdjson(user, endpoint, from, to, out);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit.ndjson\"")
                    .contentType(MediaType.parseMediaType("application/x-ndjson"))
                    .body(body);
        }

        StreamingResponseBody body = out -> exportService.streamCsv(user, endpoint, from, to, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    @DeleteMapping("/**")
    public ResponseEntity<Void> deleteNotAllowed() {
        return ResponseEntity.status(403).build();
    }
}
