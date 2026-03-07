package dev.pulsermm.commands.infrastructure.persistence;

import dev.pulsermm.commands.domain.ScriptRunResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScriptRunResultRepository extends JpaRepository<ScriptRunResult, UUID> {

    List<ScriptRunResult> findByRunId(UUID runId);

    @Query("SELECT r FROM ScriptRunResult r WHERE r.runId = :runId AND r.ackedAt IS NULL")
    List<ScriptRunResult> findPendingByRunId(@Param("runId") UUID runId);
}
