ALTER TABLE software_items ADD COLUMN app_id VARCHAR(256);
ALTER TABLE software_items ADD COLUMN update_to VARCHAR(128);
ALTER TABLE software_items ADD COLUMN is_store BOOLEAN DEFAULT false;

-- Update unique constraint to consider app_id instead of name since names can have duplicates in winget or different variants
ALTER TABLE software_items DROP CONSTRAINT software_items_endpoint_id_name_key;
ALTER TABLE software_items ADD CONSTRAINT uk_software_items_endpoint_app_id UNIQUE(endpoint_id, app_id);

ALTER TABLE software_commands ADD COLUMN app_id VARCHAR(256);
