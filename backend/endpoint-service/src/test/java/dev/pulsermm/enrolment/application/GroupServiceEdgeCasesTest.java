package dev.pulsermm.enrolment.application;

import dev.pulsermm.enrolment.domain.Group;
import dev.pulsermm.enrolment.infrastructure.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupServiceEdgeCasesTest {

    private GroupRepository groupRepo;
    private GroupService service;

    @BeforeEach
    void setUp() {
        groupRepo = mock(GroupRepository.class);
        service = new GroupService(groupRepo);
    }

    @Test
    void createRootGroupWithNoParent() {
        when(groupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.create("root", null);

        assertThat(result.name()).isEqualTo("root");
        assertThat(result.parentId()).isNull();
        verify(groupRepo).save(any());
    }

    @Test
    void createChildGroupWithValidParent() {
        UUID parentId = UUID.randomUUID();
        Group parent = new Group(parentId, "parent", null);

        when(groupRepo.findById(parentId)).thenReturn(Optional.of(parent));
        when(groupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.create("child", parentId);

        assertThat(result.name()).isEqualTo("child");
        assertThat(result.parentId()).isEqualTo(parentId);
    }

    @Test
    void createWithNonExistentParentThrows() {
        UUID parentId = UUID.randomUUID();
        when(groupRepo.findById(parentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("child", parentId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Parent group not found");
    }

    @Test
    void createWithMaxDepthExceededThrows() {
        UUID level5Id = UUID.randomUUID();
        UUID level4Id = UUID.randomUUID();
        UUID level3Id = UUID.randomUUID();
        UUID level2Id = UUID.randomUUID();
        UUID level1Id = UUID.randomUUID();

        Group level5 = new Group(level5Id, "level5", level4Id);
        Group level4 = new Group(level4Id, "level4", level3Id);
        Group level3 = new Group(level3Id, "level3", level2Id);
        Group level2 = new Group(level2Id, "level2", level1Id);
        Group level1 = new Group(level1Id, "level1", null);

        when(groupRepo.findById(level5Id)).thenReturn(Optional.of(level5));
        when(groupRepo.findById(level4Id)).thenReturn(Optional.of(level4));
        when(groupRepo.findById(level3Id)).thenReturn(Optional.of(level3));
        when(groupRepo.findById(level2Id)).thenReturn(Optional.of(level2));
        when(groupRepo.findById(level1Id)).thenReturn(Optional.of(level1));

        assertThatThrownBy(() -> service.create("level6", level5Id))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cannot exceed depth");
    }

    @Test
    void createAtMaxDepthSucceeds() {
        UUID level4Id = UUID.randomUUID();
        UUID level3Id = UUID.randomUUID();
        UUID level2Id = UUID.randomUUID();
        UUID level1Id = UUID.randomUUID();

        Group level4 = new Group(level4Id, "level4", level3Id);
        Group level3 = new Group(level3Id, "level3", level2Id);
        Group level2 = new Group(level2Id, "level2", level1Id);
        Group level1 = new Group(level1Id, "level1", null);

        when(groupRepo.findById(level4Id)).thenReturn(Optional.of(level4));
        when(groupRepo.findById(level3Id)).thenReturn(Optional.of(level3));
        when(groupRepo.findById(level2Id)).thenReturn(Optional.of(level2));
        when(groupRepo.findById(level1Id)).thenReturn(Optional.of(level1));
        when(groupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.create("level5", level4Id);

        assertThat(result.name()).isEqualTo("level5");
    }

    @Test
    void listAllReturnsEmptyList() {
        when(groupRepo.findAll()).thenReturn(List.of());

        var result = service.listAll();

        assertThat(result).isEmpty();
    }

    @Test
    void listAllReturnsMultipleGroups() {
        Group group1 = new Group(UUID.randomUUID(), "group1", null);
        Group group2 = new Group(UUID.randomUUID(), "group2", null);

        when(groupRepo.findAll()).thenReturn(List.of(group1, group2));

        var result = service.listAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("group1");
        assertThat(result.get(1).name()).isEqualTo("group2");
    }

    @Test
    void createGroupWithEmptyName() {
        when(groupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.create("", null);

        assertThat(result.name()).isEmpty();
    }

    @Test
    void createGroupWithVeryLongName() {
        String longName = "a".repeat(1000);
        when(groupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.create(longName, null);

        assertThat(result.name()).hasSize(1000);
    }
}
