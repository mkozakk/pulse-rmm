package dev.pulsermm.audit.infrastructure.persistence;

import dev.pulsermm.audit.domain.AuditEvent;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {

    default Page<AuditEvent> findFiltered(UUID userId, UUID endpointId, Instant from, Instant to,
                                           UUID orgId, Pageable pageable) {
        return findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) predicates.add(cb.equal(root.get("userId"), userId));
            if (endpointId != null) predicates.add(cb.equal(root.get("endpointId"), endpointId));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            if (orgId != null) predicates.add(cb.equal(root.get("orgId"), orgId));
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    default Page<AuditEvent> findFiltered(UUID userId, UUID endpointId, Instant from, Instant to, Pageable pageable) {
        return findFiltered(userId, endpointId, from, to, null, pageable);
    }
}
