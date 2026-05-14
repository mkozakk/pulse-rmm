package dev.pulsermm.agentupdate.infrastructure.persistence;

import dev.pulsermm.agentupdate.domain.AgentUpdateEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AgentUpdateEventRepository extends JpaRepository<AgentUpdateEvent, UUID> {}
