package dev.pulsermm.rbac.application;

import dev.pulsermm.rbac.api.ConflictException;
import dev.pulsermm.rbac.api.NotFoundException;
import dev.pulsermm.rbac.domain.Organization;
import dev.pulsermm.rbac.infrastructure.OrganizationRepository;
import dev.pulsermm.rbac.infrastructure.keycloak.KeycloakAdminClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final KeycloakAdminClient keycloakAdminClient;

    public OrganizationService(OrganizationRepository organizationRepository,
                               KeycloakAdminClient keycloakAdminClient) {
        this.organizationRepository = organizationRepository;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    @Transactional
    public Organization create(String name) {
        if (organizationRepository.existsByName(name)) {
            throw new ConflictException("Organization name already exists");
        }
        return organizationRepository.save(new Organization(name));
    }

    @Transactional(readOnly = true)
    public List<Organization> list() {
        return organizationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Organization get(UUID id) {
        return organizationRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Organization not found"));
    }

    @Transactional
    public Organization update(UUID id, String name) {
        Organization org = get(id);
        if (!org.getName().equals(name) && organizationRepository.existsByName(name)) {
            throw new ConflictException("Organization name already exists");
        }
        org.setName(name);
        return organizationRepository.save(org);
    }

    @Transactional
    public void delete(UUID id) {
        Organization org = get(id);
        if (!keycloakAdminClient.listUsersByOrg(id).isEmpty()) {
            throw new ConflictException("Organization is not empty");
        }
        organizationRepository.delete(org);
    }
}
