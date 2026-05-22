package dev.pulsermm.enrolment.infrastructure;

import dev.pulsermm.enrolment.domain.EndpointTag;
import dev.pulsermm.enrolment.domain.EndpointTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface EndpointTagRepository extends JpaRepository<EndpointTag, EndpointTagId> {

    List<EndpointTag> findAllByIdEndpointId(UUID endpointId);

    @Modifying
    @Transactional
    void deleteAllByIdEndpointId(UUID endpointId);
}
