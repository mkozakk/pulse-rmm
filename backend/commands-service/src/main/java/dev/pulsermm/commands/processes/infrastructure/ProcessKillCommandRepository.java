package dev.pulsermm.commands.processes.infrastructure;

import dev.pulsermm.commands.processes.domain.ProcessKillCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessKillCommandRepository extends JpaRepository<ProcessKillCommand, UUID> {
}
