package dev.pulsermm.enrolment.infrastructure;

import dev.pulsermm.enrolment.domain.TagRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TagRuleRepository extends JpaRepository<TagRule, UUID> {
}
