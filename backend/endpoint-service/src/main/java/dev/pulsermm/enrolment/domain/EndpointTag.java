package dev.pulsermm.enrolment.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "endpoint_tags", schema = "enrolment")
public class EndpointTag {

    @EmbeddedId
    private EndpointTagId id;

    @Column(nullable = false, length = 256)
    private String value;

    public EndpointTag() {}

    public EndpointTag(EndpointTagId id, String value) {
        this.id = id;
        this.value = value;
    }

    public EndpointTagId getId() {
        return id;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
