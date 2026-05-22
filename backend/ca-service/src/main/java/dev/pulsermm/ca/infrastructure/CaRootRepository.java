package dev.pulsermm.ca.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CaRootRepository {

    private final JdbcTemplate jdbc;

    public CaRootRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Row> findCurrent() {
        var rows = jdbc.query(
            "SELECT cert_pem, encrypted_priv_key FROM ca.ca_root ORDER BY created_at DESC LIMIT 1",
            (rs, i) -> new Row(rs.getString("cert_pem"), rs.getBytes("encrypted_priv_key")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void save(java.util.UUID id, String certPem, byte[] encryptedPrivKey) {
        jdbc.update(
            "INSERT INTO ca.ca_root (id, cert_pem, encrypted_priv_key) VALUES (?, ?, ?)",
            id, certPem, encryptedPrivKey);
    }

    public record Row(String certPem, byte[] encryptedPrivKey) {}
}
