package dev.pulsermm.enrolment.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class EndpointTagId implements Serializable {

    private UUID endpointId;
    private String key;

    public EndpointTagId() {}

    public EndpointTagId(UUID endpointId, String key) {
        this.endpointId = endpointId;
        this.key = key;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EndpointTagId that)) return false;
        return Objects.equals(endpointId, that.endpointId) && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpointId, key);
    }
}
