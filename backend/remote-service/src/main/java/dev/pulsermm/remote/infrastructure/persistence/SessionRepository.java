package dev.pulsermm.remote.infrastructure.persistence;

import dev.pulsermm.remote.domain.DesktopSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SessionRepository extends JpaRepository<DesktopSession, UUID> {}
