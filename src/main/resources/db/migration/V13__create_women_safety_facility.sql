CREATE TABLE IF NOT EXISTS women_safety_facility (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_id VARCHAR(100) NOT NULL,
  sido_name VARCHAR(40) NOT NULL,
  sigungu_name VARCHAR(60) NOT NULL,
  facility_type VARCHAR(40) NOT NULL,
  facility_name VARCHAR(180) NOT NULL,
  brand_name VARCHAR(80) NOT NULL DEFAULT '',
  address VARCHAR(300) NOT NULL,
  latitude DECIMAL(10,7) NULL,
  longitude DECIMAL(10,7) NULL,
  source_name VARCHAR(200) NOT NULL,
  source_updated_at DATE NOT NULL,
  data_year SMALLINT NULL,
  coverage_note VARCHAR(400) NOT NULL DEFAULT '',
  coordinate_validation VARCHAR(40) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT uk_women_safety_facility UNIQUE (source_name, source_id),
  CONSTRAINT chk_women_safety_type CHECK (facility_type IN ('SAFE_HOUSE', 'SAFE_STORE', 'SAFE_PARCEL_LOCKER', 'SAFE_ROUTE', 'OTHER')),
  KEY idx_women_safety_region (sido_name, sigungu_name, coordinate_validation),
  KEY idx_women_safety_coordinates (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO women_safety_facility
  (source_id, sido_name, sigungu_name, facility_type, facility_name, brand_name, address,
   latitude, longitude, source_name, source_updated_at, data_year, coverage_note,
   coordinate_validation, created_at, updated_at)
SELECT source_id, sido_name, sigungu_name, 'SAFE_HOUSE',
       TRIM(CONCAT(brand_name, ' ', store_name)), brand_name, address,
       latitude, longitude, source_name, source_updated_at, 2019,
       '서울특별시 공식 여성안심지킴이집입니다.',
       CASE WHEN geocode_status = 'VERIFIED' AND geocoded_sido = sido_name
                  AND geocoded_sigungu = sigungu_name THEN 'VERIFIED' ELSE geocode_status END,
       created_at, updated_at
FROM women_safety_guard_house
WHERE TRUE
ON DUPLICATE KEY UPDATE
  facility_name=VALUES(facility_name), brand_name=VALUES(brand_name), address=VALUES(address),
  latitude=VALUES(latitude), longitude=VALUES(longitude), source_updated_at=VALUES(source_updated_at),
  coordinate_validation=VALUES(coordinate_validation), updated_at=VALUES(updated_at);

