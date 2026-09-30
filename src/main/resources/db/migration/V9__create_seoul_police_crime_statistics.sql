CREATE TABLE IF NOT EXISTS seoul_police_crime_statistics (
  id BIGINT NOT NULL AUTO_INCREMENT,
  `year` SMALLINT NOT NULL,
  sido_name VARCHAR(40) NOT NULL,
  sigungu_name VARCHAR(40) NOT NULL,
  police_station_name VARCHAR(80) NOT NULL,
  crime_type VARCHAR(30) NOT NULL,
  occurrence_count INT NOT NULL,
  arrest_count INT NOT NULL,
  source_name VARCHAR(160) NOT NULL,
  source_updated_at DATE NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT uk_seoul_crime_station_type UNIQUE (`year`, police_station_name, crime_type),
  CONSTRAINT chk_seoul_crime_occurrence CHECK (occurrence_count >= 0),
  CONSTRAINT chk_seoul_crime_arrest CHECK (arrest_count >= 0),
  KEY idx_seoul_crime_region (`year`, sido_name, sigungu_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

