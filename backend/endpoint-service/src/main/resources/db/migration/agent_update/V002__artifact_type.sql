ALTER TABLE agent_update.agent_versions
    ADD COLUMN artifact_type VARCHAR(20) NOT NULL DEFAULT 'tar.gz';

ALTER TABLE agent_update.agent_versions
    DROP CONSTRAINT agent_versions_version_os_arch_key;

ALTER TABLE agent_update.agent_versions
    ADD CONSTRAINT agent_versions_version_os_arch_type_key
        UNIQUE (version, os, arch, artifact_type);

DROP INDEX agent_update.idx_agent_versions_current;

CREATE UNIQUE INDEX idx_agent_versions_current
    ON agent_update.agent_versions (os, arch, artifact_type)
    WHERE is_current = true;
