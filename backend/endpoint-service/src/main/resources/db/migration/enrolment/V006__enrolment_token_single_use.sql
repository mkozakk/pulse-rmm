ALTER TABLE enrolment_tokens ADD COLUMN consumed_at TIMESTAMPTZ;
ALTER TABLE enrolment_tokens ADD COLUMN consumed_by_endpoint UUID;
