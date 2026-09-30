CREATE TABLE IF NOT EXISTS property_listing (
    property_id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NULL,
    source_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    source_id VARCHAR(180) NULL,
    source_url VARCHAR(1000) NULL,
    deal_type VARCHAR(20) NOT NULL,
    building_type VARCHAR(60) NOT NULL,
    title VARCHAR(200) NOT NULL,
    address VARCHAR(300) NOT NULL,
    sido VARCHAR(60) NULL,
    sigungu VARCHAR(120) NULL,
    neighborhood VARCHAR(120) NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,
    sale_price BIGINT NULL,
    deposit BIGINT NOT NULL DEFAULT 0,
    monthly_rent BIGINT NOT NULL DEFAULT 0,
    maintenance_fee BIGINT NOT NULL DEFAULT 0,
    area DECIMAL(10,2) NULL,
    floor_text VARCHAR(40) NULL,
    parking BOOLEAN NOT NULL DEFAULT FALSE,
    elevator BOOLEAN NOT NULL DEFAULT FALSE,
    pet BOOLEAN NOT NULL DEFAULT FALSE,
    description VARCHAR(3000) NULL,
    contact VARCHAR(80) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    source_updated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (property_id),
    UNIQUE KEY uk_property_source (source_type, source_id),
    KEY idx_property_deal_status (deal_type, status),
    KEY idx_property_region (sido, sigungu, neighborhood),
    KEY idx_property_owner (owner_user_id),
    KEY idx_property_updated (updated_at)
) ENGINE=InnoDB AUTO_INCREMENT=100000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS property_listing_image (
    image_id BIGINT NOT NULL AUTO_INCREMENT,
    property_id BIGINT NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    original_name VARCHAR(255) NULL,
    stored_name VARCHAR(255) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    representative BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (image_id),
    KEY idx_property_image_property (property_id, sort_order, image_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS property_favorite (
    user_id BIGINT NOT NULL,
    property_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, property_id),
    KEY idx_property_favorite_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS property_crawl_history (
    crawl_id BIGINT NOT NULL AUTO_INCREMENT,
    source_name VARCHAR(120) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    collected_count INT NOT NULL DEFAULT 0,
    inserted_count INT NOT NULL DEFAULT 0,
    updated_count INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    message VARCHAR(1000) NULL,
    PRIMARY KEY (crawl_id),
    KEY idx_property_crawl_started (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
