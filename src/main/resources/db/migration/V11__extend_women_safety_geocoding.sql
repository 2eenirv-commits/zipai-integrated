-- 기존 여성안심 데이터를 삭제하거나 좌표를 초기화하지 않고 누락된 검증 컬럼만 추가한다.
SET @schema_name = DATABASE();

SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='women_safety_guard_house' AND column_name='geocode_status'),
  'SELECT 1',
  'ALTER TABLE women_safety_guard_house ADD COLUMN geocode_status VARCHAR(30) NOT NULL DEFAULT ''NOT_ATTEMPTED'' AFTER longitude'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='women_safety_guard_house' AND column_name='geocoded_address'),
  'SELECT 1',
  'ALTER TABLE women_safety_guard_house ADD COLUMN geocoded_address VARCHAR(300) NOT NULL DEFAULT '''' AFTER geocode_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='women_safety_guard_house' AND column_name='geocoded_sido'),
  'SELECT 1',
  'ALTER TABLE women_safety_guard_house ADD COLUMN geocoded_sido VARCHAR(40) NOT NULL DEFAULT '''' AFTER geocoded_address'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='women_safety_guard_house' AND column_name='geocoded_sigungu'),
  'SELECT 1',
  'ALTER TABLE women_safety_guard_house ADD COLUMN geocoded_sigungu VARCHAR(40) NOT NULL DEFAULT '''' AFTER geocoded_sido'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=@schema_name AND table_name='women_safety_guard_house' AND index_name='idx_women_guard_verified'),
  'SELECT 1',
  'CREATE INDEX idx_women_guard_verified ON women_safety_guard_house(geocode_status, latitude, longitude)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

