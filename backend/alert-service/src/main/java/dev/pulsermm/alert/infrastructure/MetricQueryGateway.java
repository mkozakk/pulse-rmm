package dev.pulsermm.alert.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MetricQueryGateway {

    @PersistenceContext
    private EntityManager em;

    /**
     * Returns true when every sample in the window satisfies the threshold condition.
     * False if there are no samples or any sample does not satisfy it.
     */
    public boolean conditionHolds(UUID endpointId, String metricType, String operator, double threshold, int durationSecs) {
        String comparison = ">".equals(operator) ? ">" : "<";

        // Count all samples and how many breach the threshold in the window
        String sql = "SELECT " +
                "COUNT(*) FILTER (WHERE value " + comparison + " :threshold) AS breaches, " +
                "COUNT(*) AS total " +
                "FROM public.metric_samples" +
                "WHERE endpoint_id = :endpointId " +
                "AND type = :metricType " +
                "AND sampled_at >= now() - (:durationSecs || ' seconds')::interval";

        Object[] row = (Object[]) em.createNativeQuery(sql)
                .setParameter("endpointId", endpointId)
                .setParameter("metricType", metricType)
                .setParameter("threshold", threshold)
                .setParameter("durationSecs", durationSecs)
                .getSingleResult();

        long breaches = ((Number) row[0]).longValue();
        long total = ((Number) row[1]).longValue();

        return total > 0 && breaches == total;
    }

    /**
     * Returns true when the condition has fully cleared (zero samples breach the threshold).
     */
    public boolean conditionCleared(UUID endpointId, String metricType, String operator, double threshold, int durationSecs) {
        String comparison = ">".equals(operator) ? ">" : "<";

        String sql = "SELECT " +
                "COUNT(*) FILTER (WHERE value " + comparison + " :threshold) AS breaches, " +
                "COUNT(*) AS total " +
                "FROM public.metric_samples" +
                "WHERE endpoint_id = :endpointId " +
                "AND type = :metricType " +
                "AND sampled_at >= now() - (:durationSecs || ' seconds')::interval";

        Object[] row = (Object[]) em.createNativeQuery(sql)
                .setParameter("endpointId", endpointId)
                .setParameter("metricType", metricType)
                .setParameter("threshold", threshold)
                .setParameter("durationSecs", durationSecs)
                .getSingleResult();

        long breaches = ((Number) row[0]).longValue();
        long total = ((Number) row[1]).longValue();

        return total > 0 && breaches == 0;
    }
}
