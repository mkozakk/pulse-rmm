package dev.pulsermm.integration.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "webhooks", schema = "integration")
public class Webhook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "secret_ciphertext", nullable = false)
    private byte[] secretCiphertext;

    @Column(name = "secret_kek_id", nullable = false, length = 50)
    private String secretKekId;

    @Column(name = "event_types", nullable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> eventTypes;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Webhook() {}

    public Webhook(String url, byte[] secretCiphertext, String secretKekId, List<String> eventTypes, UUID createdBy) {
        this.url = url;
        this.secretCiphertext = secretCiphertext;
        this.secretKekId = secretKekId;
        this.eventTypes = eventTypes;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public String getUrl() { return url; }
    public byte[] getSecretCiphertext() { return secretCiphertext; }
    public String getSecretKekId() { return secretKekId; }
    public List<String> getEventTypes() { return eventTypes; }
    public boolean isEnabled() { return enabled; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }

    public void setUrl(String url) { this.url = url; }
    public void setSecretCiphertext(byte[] secretCiphertext) { this.secretCiphertext = secretCiphertext; }
    public void setEventTypes(List<String> eventTypes) { this.eventTypes = eventTypes; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
