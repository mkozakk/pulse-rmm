package dev.pulsermm.enrolment.application;

import dev.pulsermm.enrolment.domain.Endpoint;
import dev.pulsermm.enrolment.domain.TagRule;
import dev.pulsermm.enrolment.infrastructure.EndpointRepository;
import dev.pulsermm.enrolment.infrastructure.TagRuleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TagRuleService {

    private final TagRuleRepository tagRuleRepository;
    private final EndpointRepository endpointRepository;
    private final JdbcTemplate jdbcTemplate;

    public TagRuleService(TagRuleRepository tagRuleRepository,
                          EndpointRepository endpointRepository,
                          JdbcTemplate jdbcTemplate) {
        this.tagRuleRepository = tagRuleRepository;
        this.endpointRepository = endpointRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void applyRulesTo(Endpoint endpoint) {
        for (TagRule rule : tagRuleRepository.findAll()) {
            String fieldValue = switch (rule.getConditionField().toLowerCase()) {
                case "os" -> endpoint.getOs();
                case "hostname" -> endpoint.getHostname();
                default -> null;
            };
            if (fieldValue != null && fieldValue.equalsIgnoreCase(rule.getConditionValue())) {
                jdbcTemplate.update(
                    "INSERT INTO enrolment.endpoint_tags (endpoint_id, key, value) VALUES (?, ?, ?) " +
                    "ON CONFLICT (endpoint_id, key) DO UPDATE SET value = EXCLUDED.value",
                    endpoint.getId(), rule.getTagKey(), rule.getTagValue()
                );
            }
        }
    }

    public void evaluateAll() {
        endpointRepository.findAll().forEach(this::applyRulesTo);
    }
}
