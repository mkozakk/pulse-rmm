package dev.pulsermm.enrolment.infrastructure;

import dev.pulsermm.enrolment.domain.EndpointRevocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EndpointRevocationRepository extends JpaRepository<EndpointRevocation, UUID> {
    boolean existsByEndpointId(UUID endpointId);
}
