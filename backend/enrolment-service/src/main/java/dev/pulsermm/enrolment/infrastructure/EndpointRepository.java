package dev.pulsermm.enrolment.infrastructure;

import dev.pulsermm.enrolment.domain.Endpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EndpointRepository extends JpaRepository<Endpoint, UUID> {
    Optional<Endpoint> findByPublicKey(byte[] publicKey);
}
