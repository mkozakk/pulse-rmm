package dev.pulsermm.commands.infrastructure.persistence;

import dev.pulsermm.commands.domain.ScriptRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ScriptRunRepository extends JpaRepository<ScriptRun, UUID> {
}
