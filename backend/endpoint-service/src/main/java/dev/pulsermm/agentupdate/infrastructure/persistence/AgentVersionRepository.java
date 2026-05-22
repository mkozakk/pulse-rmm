package dev.pulsermm.agentupdate.infrastructure.persistence;

import dev.pulsermm.agentupdate.domain.AgentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentVersionRepository extends JpaRepository<AgentVersion, UUID> {

    List<AgentVersion> findAllByOrderByPublishedAtDesc();

    Optional<AgentVersion> findByOsAndArchAndArtifactTypeAndCurrentTrue(String os, String arch, String artifactType);

    boolean existsByVersionAndOsAndArchAndArtifactType(String version, String os, String arch, String artifactType);

    @Modifying
    @Query("UPDATE AgentVersion v SET v.current = false WHERE v.os = :os AND v.arch = :arch AND v.artifactType = :artifactType AND v.current = true")
    void clearCurrentForPlatform(String os, String arch, String artifactType);
}
