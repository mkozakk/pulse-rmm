package dev.pulsermm.enrolment.api;

import dev.pulsermm.enrolment.application.TagRuleService;
import dev.pulsermm.enrolment.domain.TagRule;
import dev.pulsermm.enrolment.infrastructure.TagRuleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Tag rules", description = "Auto-tagging rules for endpoints")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tag-rules")
public class TagRuleController {

    private final TagRuleRepository tagRuleRepository;
    private final TagRuleService tagRuleService;

    public TagRuleController(TagRuleRepository tagRuleRepository, TagRuleService tagRuleService) {
        this.tagRuleRepository = tagRuleRepository;
        this.tagRuleService = tagRuleService;
    }

    @Operation(summary = "Create tag rule")
    @ApiResponse(responseCode = "201", description = "Rule created",
        content = @Content(schema = @Schema(implementation = TagRuleResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @PostMapping
    public ResponseEntity<TagRuleResponse> create(
            @RequestBody CreateTagRuleRequest request,
            Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        TagRule rule = tagRuleRepository.save(
            new TagRule(request.conditionField(), request.conditionValue(), request.tagKey(), request.tagValue())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(TagRuleResponse.from(rule));
    }

    @Operation(summary = "List tag rules")
    @ApiResponse(responseCode = "200", description = "Rule list",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = TagRuleResponse.class))))
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @GetMapping
    public ResponseEntity<List<TagRuleResponse>> list(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(tagRuleRepository.findAll().stream().map(TagRuleResponse::from).toList());
    }

    @Operation(summary = "Delete tag rule")
    @ApiResponse(responseCode = "204", description = "Rule deleted")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        tagRuleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Evaluate tag rules")
    @ApiResponse(responseCode = "200", description = "Rules evaluated")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @PostMapping("/evaluate")
    public ResponseEntity<Void> evaluate(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        tagRuleService.evaluateAll();
        return ResponseEntity.ok().build();
    }
}
