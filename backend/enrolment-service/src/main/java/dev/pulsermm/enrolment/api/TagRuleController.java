package dev.pulsermm.enrolment.api;

import dev.pulsermm.enrolment.application.TagRuleService;
import dev.pulsermm.enrolment.domain.TagRule;
import dev.pulsermm.enrolment.infrastructure.TagRuleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tag-rules")
public class TagRuleController {

    private final TagRuleRepository tagRuleRepository;
    private final TagRuleService tagRuleService;

    public TagRuleController(TagRuleRepository tagRuleRepository, TagRuleService tagRuleService) {
        this.tagRuleRepository = tagRuleRepository;
        this.tagRuleService = tagRuleService;
    }

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

    @GetMapping
    public ResponseEntity<List<TagRuleResponse>> list(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(tagRuleRepository.findAll().stream().map(TagRuleResponse::from).toList());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        tagRuleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/evaluate")
    public ResponseEntity<Void> evaluate(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        tagRuleService.evaluateAll();
        return ResponseEntity.ok().build();
    }
}
