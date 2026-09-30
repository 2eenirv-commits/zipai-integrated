CREATE TABLE IF NOT EXISTS police_crime_statistics (
  id BIGINT NOT NULL AUTO_INCREMENT,
  `year` SMALLINT NOT NULL,
  sido_name VARCHAR(40) NOT NULL,
  sigungu_name VARCHAR(80) NOT NULL DEFAULT '',
  police_station_name VARCHAR(80) NOT NULL,
  police_agency VARCHAR(80) NOT NULL,
  crime_type VARCHAR(30) NOT NULL,
  occurrence_count INT NOT NULL,
  arrest_count INT NOT NULL,
  provisional BOOLEAN NOT NULL DEFAULT FALSE,
  coverage_type VARCHAR(40) NOT NULL,
  coverage_note VARCHAR(300) NOT NULL DEFAULT '',
  source_name VARCHAR(200) NOT NULL,
  source_updated_at DATE NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT uk_police_crime_stat UNIQUE (`year`, police_agency, police_station_name, crime_type),
  CONSTRAINT chk_police_crime_occurrence CHECK (occurrence_count >= 0),
  CONSTRAINT chk_police_crime_arrest CHECK (arrest_count >= 0),
  CONSTRAINT chk_police_crime_type CHECK (crime_type IN ('살인', '강도', '강간·추행', '절도', '폭력')),
  CONSTRAINT chk_police_crime_coverage CHECK (coverage_type IN ('POLICE_STATION_STATISTICS', 'POLICE_AGENCY_STATISTICS')),
  KEY idx_police_crime_region (`year`, sido_name, sigungu_name),
  KEY idx_police_crime_agency (`year`, police_agency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO police_crime_statistics
  (`year`, sido_name, sigungu_name, police_station_name, police_agency, crime_type,
   occurrence_count, arrest_count, provisional, coverage_type, coverage_note,
   source_name, source_updated_at, created_at, updated_at)
SELECT `year`, sido_name, sigungu_name, police_station_name, '서울특별시경찰청', crime_type,
       occurrence_count, arrest_count, FALSE, 'POLICE_STATION_STATISTICS',
       '경찰서 관할 기준 통계입니다.', source_name, source_updated_at, created_at, updated_at
FROM seoul_police_crime_statistics
WHERE TRUE
ON DUPLICATE KEY UPDATE
  sido_name=VALUES(sido_name), sigungu_name=VALUES(sigungu_name),
  occurrence_count=VALUES(occurrence_count), arrest_count=VALUES(arrest_count),
  source_name=VALUES(source_name), source_updated_at=VALUES(source_updated_at),
  updated_at=VALUES(updated_at);

