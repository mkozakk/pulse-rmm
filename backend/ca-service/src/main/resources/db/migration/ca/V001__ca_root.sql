CREATE TABLE ca_root (
    id                 UUID PRIMARY KEY,
    cert_pem           TEXT NOT NULL,
    encrypted_priv_key BYTEA NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
