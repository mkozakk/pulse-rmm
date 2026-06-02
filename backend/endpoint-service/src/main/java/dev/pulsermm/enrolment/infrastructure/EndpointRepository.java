package dev.pulsermm.enrolment.infrastructure;

import dev.pulsermm.enrolment.domain.Endpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EndpointRepository extends JpaRepository<Endpoint, UUID> {
    Optional<Endpoint> findByPublicKey(byte[] publicKey);

    @Query("SELECT e FROM Endpoint e WHERE e.groupId IN (SELECT g.id FROM Group g WHERE g.orgId = :orgId)")
    List<Endpoint> findAllByOrg(@Param("orgId") UUID orgId);
}
