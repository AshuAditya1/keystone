CREATE TABLE keystone_schema_metadata (
    metadata_key VARCHAR(100) PRIMARY KEY,
    metadata_value VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO keystone_schema_metadata (metadata_key, metadata_value)
VALUES ('schema_baseline', 'day-1');
