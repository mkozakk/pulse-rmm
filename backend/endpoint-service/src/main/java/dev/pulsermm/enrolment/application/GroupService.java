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

    public GroupResponse create(String name, UUID parentId) {
        if (parentId != null) {
            if (groupRepository.findById(parentId).isEmpty()) {
                throw new IllegalStateException("Parent group not found: " + parentId);
            }
            if (depth(parentId) >= MAX_DEPTH) {
                throw new IllegalStateException("Group tree cannot exceed depth " + MAX_DEPTH);
            }
        }

        Group group = new Group(UUID.randomUUID(), name, parentId);
        groupRepository.save(group);
        return new GroupResponse(group.getId(), group.getName(), group.getParentId());
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> listAll() {
        return groupRepository.findAll().stream()
            .map(g -> new GroupResponse(g.getId(), g.getName(), g.getParentId()))
            .toList();
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
