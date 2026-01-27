package dev.pulsermm.enrolment.application;

import dev.pulsermm.enrolment.api.TagEntry;
import dev.pulsermm.enrolment.domain.EndpointTag;
import dev.pulsermm.enrolment.domain.EndpointTagId;
import dev.pulsermm.enrolment.infrastructure.EndpointRepository;
import dev.pulsermm.enrolment.infrastructure.EndpointTagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TagService {

    private final EndpointRepository endpointRepository;
    private final EndpointTagRepository endpointTagRepository;

    public TagService(EndpointRepository endpointRepository, EndpointTagRepository endpointTagRepository) {
        this.endpointRepository = endpointRepository;
        this.endpointTagRepository = endpointTagRepository;
    }

    public void setTags(UUID endpointId, List<TagEntry> tags) {
        if (!endpointRepository.existsById(endpointId)) {
            throw new IllegalArgumentException("Endpoint not found: " + endpointId);
        }
        endpointTagRepository.deleteAllByIdEndpointId(endpointId);
        for (TagEntry tag : tags) {
            endpointTagRepository.save(new EndpointTag(new EndpointTagId(endpointId, tag.key()), tag.value()));
        }
    }
}
