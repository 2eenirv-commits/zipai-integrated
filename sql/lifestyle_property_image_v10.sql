SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS property_image (
    image_id BIGINT NOT NULL AUTO_INCREMENT,
    property_id BIGINT NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    representative BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (image_id),
    UNIQUE KEY uk_property_image_stored_name (stored_name),
    KEY idx_property_image_property (property_id, sort_order),
    CONSTRAINT fk_property_image_property
        FOREIGN KEY (property_id)
        REFERENCES lifestyle_property(property_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT COUNT(*) AS property_image_count
FROM property_image;
