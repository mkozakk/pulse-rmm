package dev.pulsermm.commands.infrastructure.persistence;

import dev.pulsermm.commands.domain.Script;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ScriptRepository extends JpaRepository<Script, UUID> {

    @Query("SELECT s FROM Script s WHERE s.approvedAt IS NULL ORDER BY s.createdAt DESC")
    Page<Script> findPendingScripts(Pageable pageable);

    @Query("SELECT s FROM Script s WHERE s.approvedAt IS NOT NULL ORDER BY s.createdAt DESC")
    Page<Script> findApprovedScripts(Pageable pageable);

    @Query("SELECT s FROM Script s ORDER BY s.createdAt DESC")
    Page<Script> findAllScripts(Pageable pageable);

    @Query("SELECT s FROM Script s WHERE s.createdBy = :userId ORDER BY s.createdAt DESC")
    Page<Script> findByCreatedBy(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT s FROM Script s WHERE (s.orgId = :orgId OR s.isGlobal = true) ORDER BY s.createdAt DESC")
    Page<Script> findVisibleToOrg(@Param("orgId") UUID orgId, Pageable pageable);

    @Query("SELECT s FROM Script s WHERE (s.orgId = :orgId OR s.isGlobal = true) AND s.approvedAt IS NULL ORDER BY s.createdAt DESC")
    Page<Script> findPendingForOrg(@Param("orgId") UUID orgId, Pageable pageable);

    @Query("SELECT s FROM Script s WHERE (s.orgId = :orgId OR s.isGlobal = true) AND s.approvedAt IS NOT NULL ORDER BY s.createdAt DESC")
    Page<Script> findApprovedForOrg(@Param("orgId") UUID orgId, Pageable pageable);
}
