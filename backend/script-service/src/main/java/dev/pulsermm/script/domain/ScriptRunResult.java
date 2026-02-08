package dev.pulsermm.script.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "script_run_results")
public class ScriptRunResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(name = "executed_at")
    private OffsetDateTime executedAt;

    @Column(name = "acked_at")
    private OffsetDateTime ackedAt;

    public ScriptRunResult() {
    }

    public ScriptRunResult(UUID runId, UUID endpointId) {
        this.runId = runId;
        this.endpointId = endpointId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(UUID endpointId) {
        this.endpointId = endpointId;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public OffsetDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(OffsetDateTime executedAt) {
        this.executedAt = executedAt;
    }

    public OffsetDateTime getAckedAt() {
        return ackedAt;
    }

    public void setAckedAt(OffsetDateTime ackedAt) {
        this.ackedAt = ackedAt;
    }

    public boolean isPending() {
        return ackedAt == null;
    }

    public boolean isComplete() {
        return ackedAt != null;
    }
}
