package dev.pulsermm.enrolment.application;

import dev.pulsermm.enrolment.api.dto.GroupResponse;
import dev.pulsermm.enrolment.domain.Group;
import dev.pulsermm.enrolment.infrastructure.GroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class GroupService {

    static final int MAX_DEPTH = 5;

    private final GroupRepository groupRepository;

    public GroupService(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    public GroupResponse create(String name, UUID parentId, UUID orgId) {
        if (parentId != null) {
            Group parent = groupRepository.findById(parentId)
                .orElseThrow(() -> new IllegalStateException("Parent group not found: " + parentId));
            // A child group must live in the same org as its parent.
            if (orgId != null && parent.getOrgId() != null && !orgId.equals(parent.getOrgId())) {
                throw new IllegalStateException("Parent group belongs to a different organization");
            }
            if (depth(parentId) >= MAX_DEPTH) {
                throw new IllegalStateException("Group tree cannot exceed depth " + MAX_DEPTH);
            }
        }

        Group group = new Group(UUID.randomUUID(), name, parentId, orgId);
        groupRepository.save(group);
        return new GroupResponse(group.getId(), group.getName(), group.getParentId());
    }

    public GroupResponse create(String name, UUID parentId) {
        return create(name, parentId, null);
    }

    // orgId == null means the caller is a global admin and sees every org's groups.
    @Transactional(readOnly = true)
    public List<GroupResponse> listForOrg(UUID orgId) {
        var groups = orgId == null ? groupRepository.findAll() : groupRepository.findByOrgId(orgId);
        return groups.stream()
            .map(g -> new GroupResponse(g.getId(), g.getName(), g.getParentId()))
            .toList();
    }

    @Transactional(readOnly = true)
    public UUID orgOf(UUID groupId) {
        return groupRepository.findById(groupId).map(Group::getOrgId).orElse(null);
    }

    private int depth(UUID groupId) {
        int d = 1;
        UUID current = groupId;
        while (true) {
            Group g = groupRepository.findById(current).orElseThrow();
            if (g.getParentId() == null) return d;
            current = g.getParentId();
            d++;
        }
    }
}
