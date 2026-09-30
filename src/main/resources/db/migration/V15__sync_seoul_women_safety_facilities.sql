INSERT INTO women_safety_facility
  (source_id, sido_name, sigungu_name, facility_type, facility_name, brand_name, address,
   latitude, longitude, source_name, source_updated_at, data_year, coverage_note,
   coordinate_validation, created_at, updated_at)
SELECT source_id, sido_name, sigungu_name, 'SAFE_HOUSE',
       TRIM(CONCAT(brand_name, ' ', store_name)), brand_name, address,
       latitude, longitude, source_name, source_updated_at, 2019,
       '서울특별시 공식 여성안심지킴이집입니다.',
       CASE WHEN geocode_status = 'VERIFIED'
                  AND geocoded_sido = sido_name
                  AND geocoded_sigungu = sigungu_name
            THEN 'VERIFIED' ELSE geocode_status END,
       created_at, updated_at
FROM women_safety_guard_house
WHERE TRUE
ON DUPLICATE KEY UPDATE
  sido_name=VALUES(sido_name), sigungu_name=VALUES(sigungu_name),
  facility_type=VALUES(facility_type), facility_name=VALUES(facility_name),
  brand_name=VALUES(brand_name), address=VALUES(address),
  latitude=VALUES(latitude), longitude=VALUES(longitude),
  source_updated_at=VALUES(source_updated_at), data_year=VALUES(data_year),
  coverage_note=VALUES(coverage_note),
  coordinate_validation=VALUES(coordinate_validation), updated_at=VALUES(updated_at);
