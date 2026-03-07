package dev.pulsermm.commands.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "script_runs", schema = "scripts")
public class ScriptRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "script_id", nullable = false)
    private UUID scriptId;

    @Column(name = "initiated_by", nullable = false)
    private UUID initiatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public ScriptRun() {
    }

    public ScriptRun(UUID scriptId, UUID initiatedBy) {
        this.scriptId = scriptId;
        this.initiatedBy = initiatedBy;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getScriptId() {
        return scriptId;
    }

    public void setScriptId(UUID scriptId) {
        this.scriptId = scriptId;
    }

    public UUID getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(UUID initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
