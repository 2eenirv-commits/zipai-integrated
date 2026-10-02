ALTER TABLE property_listing
    ADD COLUMN source_name VARCHAR(120) NULL AFTER source_type;

ALTER TABLE property_listing
    ADD COLUMN study_data BOOLEAN NOT NULL DEFAULT FALSE AFTER source_name;

ALTER TABLE property_listing
    ADD COLUMN import_run_id VARCHAR(64) NULL AFTER source_id;

ALTER TABLE property_listing
    ADD COLUMN last_seen_at DATETIME(6) NULL AFTER source_updated_at;

ALTER TABLE property_listing
    ADD KEY idx_property_source_name_status (source_name, status);

ALTER TABLE property_listing
    ADD KEY idx_property_study_status (study_data, status);

UPDATE property_listing
SET source_name = 'ZIPAI_STUDY_LISTING', study_data = TRUE
WHERE source_type = 'CRAWLING' AND title LIKE '[학습용]%';
