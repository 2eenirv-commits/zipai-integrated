CREATE TABLE IF NOT EXISTS safe_area_centers (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  address VARCHAR(255) NOT NULL,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  source_name VARCHAR(120) NOT NULL,
  source_updated_at DATE NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_safe_area_location (address, latitude, longitude),
  KEY idx_safe_area_name (name),
  KEY idx_safe_area_address (address)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS safe_infrastructure (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_id VARCHAR(128) NOT NULL,
  area_id BIGINT NULL,
  facility_type VARCHAR(30) NOT NULL,
  name VARCHAR(120) NOT NULL,
  address VARCHAR(255) NOT NULL,
  latitude DOUBLE NULL,
  longitude DOUBLE NULL,
  purpose VARCHAR(50) NULL,
  camera_count INT NOT NULL DEFAULT 1,
  source_name VARCHAR(120) NOT NULL,
  source_updated_at DATE NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_safe_infra_source_id (source_id),
  KEY idx_safe_infra_type (facility_type),
  KEY idx_safe_infra_lat_lng (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

