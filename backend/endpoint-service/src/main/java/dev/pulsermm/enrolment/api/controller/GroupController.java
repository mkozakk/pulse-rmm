package dev.pulsermm.enrolment.api.controller;

import dev.pulsermm.enrolment.api.dto.CreateGroupRequest;
import dev.pulsermm.enrolment.api.dto.GroupResponse;
import dev.pulsermm.enrolment.application.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Groups", description = "Endpoint grouping")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @Operation(summary = "Create group")
    @ApiResponse(responseCode = "201", description = "Group created",
        content = @Content(schema = @Schema(implementation = GroupResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @PostMapping
    public ResponseEntity<GroupResponse> create(
            @Valid @RequestBody CreateGroupRequest request,
            @RequestHeader(value = "X-User-Org-Id", required = false) UUID orgId,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        GroupResponse created = groupService.create(request.name(), request.parentId(), orgId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "List groups")
    @ApiResponse(responseCode = "200", description = "Group list",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = GroupResponse.class))))
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @GetMapping
    public ResponseEntity<List<GroupResponse>> list(
            @RequestHeader(value = "X-User-Org-Id", required = false) UUID orgId,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(groupService.listForOrg(orgId));
    }
}
