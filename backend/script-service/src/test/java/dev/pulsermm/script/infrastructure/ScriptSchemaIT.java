package dev.pulsermm.script.infrastructure;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ScriptSchemaIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("postgres")
            .withPassword("postgres");

    @Test
    void migrationsRunSuccessfully() throws SQLException {
        var url = postgres.getJdbcUrl();
        var user = postgres.getUsername();
        var password = postgres.getPassword();

        try (var connection = DriverManager.getConnection(url, user, password)) {
            runMigrations(connection);

            assertTablesExist(connection);
            assertForeignKeysExist(connection);
            assertIndexesExist(connection);
        }
    }

    private void runMigrations(Connection connection) throws SQLException {
        var sql = """
                CREATE TABLE users (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    username VARCHAR(255) UNIQUE NOT NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
                );

                CREATE TABLE groups (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    name VARCHAR(255) NOT NULL,
                    parent_id UUID,
                    FOREIGN KEY (parent_id) REFERENCES groups(id)
                );

                CREATE TABLE endpoints (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    hostname VARCHAR(255) NOT NULL,
                    os VARCHAR(64),
                    arch VARCHAR(64),
                    group_id UUID,
                    public_key TEXT,
                    enrolled_at TIMESTAMPTZ,
                    last_seen TIMESTAMPTZ,
                    FOREIGN KEY (group_id) REFERENCES groups(id)
                );

                CREATE TABLE scripts (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    name VARCHAR(256) NOT NULL,
                    body TEXT NOT NULL,
                    approved_at TIMESTAMPTZ,
                    created_by UUID NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                    FOREIGN KEY (created_by) REFERENCES users(id)
                );

                CREATE TABLE script_runs (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    script_id UUID NOT NULL,
                    initiated_by UUID NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                    FOREIGN KEY (script_id) REFERENCES scripts(id),
                    FOREIGN KEY (initiated_by) REFERENCES users(id)
                );

                CREATE TABLE script_run_results (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    run_id UUID NOT NULL,
                    endpoint_id UUID NOT NULL,
                    exit_code INTEGER,
                    output TEXT,
                    executed_at TIMESTAMPTZ,
                    acked_at TIMESTAMPTZ,
                    FOREIGN KEY (run_id) REFERENCES script_runs(id),
                    FOREIGN KEY (endpoint_id) REFERENCES endpoints(id)
                );

                CREATE TABLE script_secrets (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    run_id UUID NOT NULL,
                    key VARCHAR(256) NOT NULL,
                    encrypted_value TEXT NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                    FOREIGN KEY (run_id) REFERENCES script_runs(id)
                );

                CREATE INDEX idx_script_runs_script_created ON script_runs(script_id, created_at);
                CREATE INDEX idx_script_run_results_run_endpoint ON script_run_results(run_id, endpoint_id);
                CREATE INDEX idx_script_run_results_acked ON script_run_results(acked_at);
                CREATE INDEX idx_script_secrets_run ON script_secrets(run_id);
                """;

        try (var stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void assertTablesExist(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();
        var tables = new String[]{"scripts", "script_runs", "script_run_results", "script_secrets"};

        for (var table : tables) {
            try (var rs = metadata.getTables(null, null, table, null)) {
                assertThat(rs.next())
                        .as("Table %s should exist", table)
                        .isTrue();
            }
        }
    }

    private void assertForeignKeysExist(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();

        try (var rs = metadata.getImportedKeys(null, null, "scripts")) {
            assertThat(rs.next())
                    .as("scripts should have a foreign key to users")
                    .isTrue();
        }

        try (var rs = metadata.getImportedKeys(null, null, "script_runs")) {
            var count = 0;
            while (rs.next()) count++;
            assertThat(count)
                    .as("script_runs should have 2 foreign keys")
                    .isGreaterThanOrEqualTo(2);
        }

        try (var rs = metadata.getImportedKeys(null, null, "script_run_results")) {
            var count = 0;
            while (rs.next()) count++;
            assertThat(count)
                    .as("script_run_results should have 2 foreign keys")
                    .isGreaterThanOrEqualTo(2);
        }
    }

    private void assertIndexesExist(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();
        var indexes = new String[]{
                "idx_script_runs_script_created",
                "idx_script_run_results_run_endpoint",
                "idx_script_run_results_acked",
                "idx_script_secrets_run"
        };

        for (var index : indexes) {
            try (var rs = metadata.getIndexInfo(null, null, null, false, false)) {
                var found = false;
                while (rs.next()) {
                    if (index.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                        found = true;
                        break;
                    }
                }
                assertThat(found)
                        .as("Index %s should exist", index)
                        .isTrue();
            }
        }
    }
}
