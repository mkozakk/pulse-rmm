CREATE TABLE alert_rules (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(120) NOT NULL,
    metric_type   VARCHAR(20)  NOT NULL,
    operator      VARCHAR(2)   NOT NULL,
    threshold     DOUBLE PRECISION NOT NULL,
    duration_secs INT NOT NULL,
    target_type   VARCHAR(10)  NOT NULL,
    target_value  VARCHAR(200) NOT NULL,
    enabled       BOOLEAN NOT NULL DEFAULT true,
    created_by    UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
