package dev.pulsermm.commands.infrastructure.persistence;

import dev.pulsermm.commands.domain.ScriptSecret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScriptSecretRepository extends JpaRepository<ScriptSecret, UUID> {

    List<ScriptSecret> findByRunId(UUID runId);
}
