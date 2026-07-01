CREATE TABLE IF NOT EXISTS url_mapping (
    id BIGSERIAL PRIMARY KEY,
    long_url VARCHAR(2048) NOT NULL,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    url_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP,
    hit_count BIGINT NOT NULL,
    last_accessed_at TIMESTAMP,
    CONSTRAINT uk_url_mapping_url_hash UNIQUE (url_hash)
);

CREATE INDEX IF NOT EXISTS idx_url_mapping_active_expires
    ON url_mapping (is_active, expires_at)
    WHERE is_active = true AND expires_at IS NOT NULL;
