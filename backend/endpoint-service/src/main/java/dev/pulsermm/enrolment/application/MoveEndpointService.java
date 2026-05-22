package dev.pulsermm.enrolment.application;

import dev.pulsermm.enrolment.domain.Endpoint;
import dev.pulsermm.enrolment.infrastructure.EndpointRepository;
import dev.pulsermm.enrolment.infrastructure.GroupRepository;
import dev.pulsermm.enrolment.infrastructure.IdentityServiceClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class MoveEndpointService {

    private final EndpointRepository endpointRepository;
    private final GroupRepository groupRepository;
    private final IdentityServiceClient identityClient;

    public MoveEndpointService(EndpointRepository endpointRepository,
                                GroupRepository groupRepository,
                                IdentityServiceClient identityClient) {
        this.endpointRepository = endpointRepository;
        this.groupRepository = groupRepository;
        this.identityClient = identityClient;
    }

    public void move(UUID endpointId, UUID newGroupId) {
        Endpoint endpoint = endpointRepository.findById(endpointId)
            .orElseThrow(() -> new IllegalArgumentException("Endpoint not found: " + endpointId));
        if (!groupRepository.existsById(newGroupId)) {
            throw new IllegalArgumentException("Group not found: " + newGroupId);
        }
        endpoint.setGroupId(newGroupId);
        endpointRepository.save(endpoint);
        identityClient.setEndpointGroup(endpointId, newGroupId);
    }
}
