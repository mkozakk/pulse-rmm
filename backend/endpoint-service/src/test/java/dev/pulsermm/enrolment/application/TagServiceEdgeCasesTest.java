package dev.pulsermm.enrolment.application;

import dev.pulsermm.enrolment.api.dto.TagEntry;
import dev.pulsermm.enrolment.domain.EndpointTag;
import dev.pulsermm.enrolment.domain.EndpointTagId;
import dev.pulsermm.enrolment.infrastructure.EndpointRepository;
import dev.pulsermm.enrolment.infrastructure.EndpointTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TagServiceEdgeCasesTest {

    private EndpointRepository endpointRepo;
    private EndpointTagRepository tagRepo;
    private TagService service;

    @BeforeEach
    void setUp() {
        endpointRepo = mock(EndpointRepository.class);
        tagRepo = mock(EndpointTagRepository.class);
        service = new TagService(endpointRepo, tagRepo);
    }

    @Test
    void setTagsForNonExistentEndpointThrows() {
        UUID endpointId = UUID.randomUUID();
        when(endpointRepo.existsById(endpointId)).thenReturn(false);

        assertThatThrownBy(() -> service.setTags(endpointId, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Endpoint not found");
    }

    @Test
    void setTagsWithEmptyListClearsExisting() {
        UUID endpointId = UUID.randomUUID();
        when(endpointRepo.existsById(endpointId)).thenReturn(true);

        service.setTags(endpointId, List.of());

        verify(tagRepo).deleteAllByIdEndpointId(endpointId);
    }

    @Test
    void setTagsWithSingleTag() {
        UUID endpointId = UUID.randomUUID();
        TagEntry tag = new TagEntry("env", "prod");

        when(endpointRepo.existsById(endpointId)).thenReturn(true);
        when(tagRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setTags(endpointId, List.of(tag));

        verify(tagRepo).deleteAllByIdEndpointId(endpointId);
        verify(tagRepo).save(argThat(t ->
            t.getId().getEndpointId().equals(endpointId) &&
            "env".equals(t.getId().getKey()) &&
            "prod".equals(t.getValue())
        ));
    }

    @Test
    void setTagsWithMultipleTags() {
        UUID endpointId = UUID.randomUUID();
        List<TagEntry> tags = List.of(
            new TagEntry("env", "prod"),
            new TagEntry("team", "backend"),
            new TagEntry("region", "us-east-1")
        );

        when(endpointRepo.existsById(endpointId)).thenReturn(true);
        when(tagRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setTags(endpointId, tags);

        verify(tagRepo).deleteAllByIdEndpointId(endpointId);
        verify(tagRepo, times(3)).save(any());
    }

    @Test
    void setTagsDeletesOldBeforeAddingNew() {
        UUID endpointId = UUID.randomUUID();
        List<TagEntry> tags = List.of(new TagEntry("new", "value"));

        when(endpointRepo.existsById(endpointId)).thenReturn(true);
        when(tagRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setTags(endpointId, tags);

        InOrder inOrder = inOrder(tagRepo);
        inOrder.verify(tagRepo).deleteAllByIdEndpointId(endpointId);
        inOrder.verify(tagRepo).save(any());
    }

    @Test
    void setTagsWithEmptyKeyAndValue() {
        UUID endpointId = UUID.randomUUID();
        TagEntry tag = new TagEntry("", "");

        when(endpointRepo.existsById(endpointId)).thenReturn(true);
        when(tagRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setTags(endpointId, List.of(tag));

        verify(tagRepo).save(argThat(t ->
            "".equals(t.getId().getKey()) &&
            "".equals(t.getValue())
        ));
    }

    @Test
    void setTagsWithSpecialCharactersInKeyAndValue() {
        UUID endpointId = UUID.randomUUID();
        TagEntry tag = new TagEntry("app-name:v2", "value-with-special!@#");

        when(endpointRepo.existsById(endpointId)).thenReturn(true);
        when(tagRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setTags(endpointId, List.of(tag));

        verify(tagRepo).save(argThat(t ->
            "app-name:v2".equals(t.getId().getKey()) &&
            "value-with-special!@#".equals(t.getValue())
        ));
    }

    @Test
    void setTagsWithDuplicateKeys() {
        UUID endpointId = UUID.randomUUID();
        List<TagEntry> tags = List.of(
            new TagEntry("env", "prod"),
            new TagEntry("env", "staging")
        );

        when(endpointRepo.existsById(endpointId)).thenReturn(true);
        when(tagRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setTags(endpointId, tags);

        verify(tagRepo, times(2)).save(any());
    }

    @Test
    void setTagsWithVeryLongKeyAndValue() {
        UUID endpointId = UUID.randomUUID();
        String longKey = "k".repeat(500);
        String longValue = "v".repeat(500);
        TagEntry tag = new TagEntry(longKey, longValue);

        when(endpointRepo.existsById(endpointId)).thenReturn(true);
        when(tagRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setTags(endpointId, List.of(tag));

        verify(tagRepo).save(argThat(t ->
            t.getId().getKey().length() == 500 &&
            t.getValue().length() == 500
        ));
    }
}
