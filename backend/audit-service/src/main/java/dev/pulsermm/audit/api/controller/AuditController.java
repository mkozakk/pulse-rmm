package dev.pulsermm.audit.api.controller;

import dev.pulsermm.audit.api.dto.AuditEventResponse;
import dev.pulsermm.audit.application.AuditExportService;
import dev.pulsermm.audit.application.AuditQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

import java.time.Instant;
import java.util.UUID;

@Tag(name = "Audit", description = "Immutable audit log — query and export compliance records")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditQueryService queryService;
    private final AuditExportService exportService;

    public AuditController(AuditQueryService queryService, AuditExportService exportService) {
        this.queryService = queryService;
        this.exportService = exportService;
    }

    @Operation(summary = "List audit events", description = "Returns a paginated list of audit events. All filters are optional and can be combined.")
    @ApiResponse(responseCode = "200", description = "Audit events returned")
    @GetMapping
    public Page<AuditEventResponse> list(
            @Parameter(description = "Filter by user ID") @RequestParam(required = false) UUID user,
            @Parameter(description = "Filter by endpoint ID") @RequestParam(required = false) UUID endpoint,
            @Parameter(description = "Start of time range (ISO-8601)") @RequestParam(required = false) Instant from,
            @Parameter(description = "End of time range (ISO-8601)") @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, capped at 200") @RequestParam(defaultValue = "50") int size,
            @RequestHeader(value = "X-User-Org-Id", required = false) UUID orgId) {
        int cappedSize = Math.min(size, 200);
        return queryService.list(user, endpoint, from, to, orgId, PageRequest.of(page, cappedSize))
                .map(AuditEventResponse::from);
    }

    @Operation(summary = "Export audit events", description = "Streams all matching audit events as a CSV or NDJSON file download. Same filters as the list endpoint.")
    @ApiResponse(responseCode = "200", description = "File download started (text/csv or application/x-ndjson)")
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @Parameter(description = "Filter by user ID") @RequestParam(required = false) UUID user,
            @Parameter(description = "Filter by endpoint ID") @RequestParam(required = false) UUID endpoint,
            @Parameter(description = "Start of time range (ISO-8601)") @RequestParam(required = false) Instant from,
            @Parameter(description = "End of time range (ISO-8601)") @RequestParam(required = false) Instant to,
            @Parameter(description = "Export format: csv or json", example = "csv") @RequestParam(defaultValue = "csv") String format) {

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

    @Operation(summary = "Delete not allowed", description = "Audit records are immutable — this endpoint always returns 403.")
    @ApiResponse(responseCode = "403", description = "Deletion of audit records is not permitted")
    @DeleteMapping("/**")
    public ResponseEntity<Void> deleteNotAllowed() {
        return ResponseEntity.status(403).build();
    }
}
