package dev.pulsermm.alert.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class EndpointResolver {

    @PersistenceContext
    private EntityManager em;

    public List<UUID> resolve(String targetType, String targetValue) {
        if ("group".equals(targetType)) {
            return resolveByGroup(targetValue);
        } else if ("tag".equals(targetType)) {
            return resolveByTag(targetValue);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<UUID> resolveByGroup(String groupId) {
        return em.createNativeQuery(
                "SELECT id FROM enrolment.endpoints WHERE group_id = CAST(:groupId AS uuid)")
                .setParameter("groupId", groupId)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<UUID> resolveByTag(String keyValue) {
        int sep = keyValue.indexOf('=');
        if (sep < 0) return List.of();
        String key = keyValue.substring(0, sep);
        String value = keyValue.substring(sep + 1);
        return em.createNativeQuery(
                "SELECT e.id FROM enrolment.endpoints e " +
                "JOIN enrolment.endpoint_tags t ON t.endpoint_id = e.id " +
                "WHERE t.key = :key AND t.value = :value")
                .setParameter("key", key)
                .setParameter("value", value)
                .getResultList();
    }
}
