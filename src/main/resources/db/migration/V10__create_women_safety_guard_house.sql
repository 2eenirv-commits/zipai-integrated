CREATE TABLE IF NOT EXISTS women_safety_guard_house (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_id VARCHAR(80) NOT NULL,
  brand_name VARCHAR(80) NOT NULL,
  store_name VARCHAR(160) NOT NULL DEFAULT '',
  sido_name VARCHAR(40) NOT NULL,
  sigungu_name VARCHAR(40) NOT NULL,
  address VARCHAR(300) NOT NULL,
  latitude DECIMAL(10,7) NULL,
  longitude DECIMAL(10,7) NULL,
  source_name VARCHAR(160) NOT NULL,
  source_updated_at DATE NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT uk_women_guard_source UNIQUE (source_name, source_id),
  KEY idx_women_guard_region (sido_name, sigungu_name),
  KEY idx_women_guard_lat_lng (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

