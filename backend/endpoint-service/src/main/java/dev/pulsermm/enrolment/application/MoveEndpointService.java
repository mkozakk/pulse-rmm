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
        var target = groupRepository.findById(newGroupId)
            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + newGroupId));
        var current = groupRepository.findById(endpoint.getGroupId()).orElse(null);
        UUID currentOrg = current != null ? current.getOrgId() : null;
        if (!java.util.Objects.equals(currentOrg, target.getOrgId())) {
            throw new CrossOrgMoveException("Cannot move endpoint across organizations");
        }
        endpoint.setGroupId(newGroupId);
        endpointRepository.save(endpoint);
        identityClient.setEndpointGroup(endpointId, newGroupId);
    }
}
