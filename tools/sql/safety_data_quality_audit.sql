-- ZipAI Safety data-quality audit (READ ONLY)
-- Run inside MySQL after selecting the zipai database.

SELECT COUNT(*) AS total_count
FROM safe_infrastructure;

SELECT facility_type, COUNT(*) AS cnt
FROM safe_infrastructure
GROUP BY facility_type
ORDER BY facility_type;

SELECT
    SUM(latitude IS NULL AND longitude IS NULL) AS both_coordinates_missing,
    SUM(latitude IS NULL AND longitude IS NOT NULL) AS latitude_missing_only,
    SUM(latitude IS NOT NULL AND longitude IS NULL) AS longitude_missing_only,
    SUM(
        latitude IS NOT NULL
        AND longitude IS NOT NULL
        AND (latitude < -90 OR latitude > 90 OR longitude < -180 OR longitude > 180)
    ) AS out_of_wgs84_range,
    SUM(
        latitude IS NOT NULL
        AND longitude IS NOT NULL
        AND latitude BETWEEN -90 AND 90
        AND longitude BETWEEN -180 AND 180
    ) AS usable_coordinate_rows
FROM safe_infrastructure;

SELECT source_id, COUNT(*) AS duplicate_count
FROM safe_infrastructure
GROUP BY source_id
HAVING COUNT(*) > 1
ORDER BY duplicate_count DESC, source_id
LIMIT 100;

-- Finds unexpected facility types. Expected current import types:
-- CCTV, EMERGENCY_BELL, SECURITY_LIGHT
-- Police data, when separately geocoded/imported, may add POLICE_STATION/POLICE_BOX.
SELECT facility_type, COUNT(*) AS cnt
FROM safe_infrastructure
WHERE facility_type NOT IN (
    'CCTV',
    'EMERGENCY_BELL',
    'SECURITY_LIGHT',
    'SAFETY_BELL',
    'STREET_LIGHT',
    'POLICE_STATION',
    'POLICE_BOX'
)
GROUP BY facility_type
ORDER BY cnt DESC;

-- Show rows unusable for distance/map/score calculation.
SELECT id, source_id, facility_type, name, address, latitude, longitude, source_name
FROM safe_infrastructure
WHERE latitude IS NULL
   OR longitude IS NULL
   OR latitude < -90 OR latitude > 90
   OR longitude < -180 OR longitude > 180
ORDER BY id
LIMIT 100;

-- IMPORTANT:
-- Do not run a DELETE until the counts above have been reviewed.
-- After the rejected pool has been generated and backed up, unusable rows can be removed with:
--
-- DELETE FROM safe_infrastructure
-- WHERE latitude IS NULL
--    OR longitude IS NULL
--    OR latitude < -90 OR latitude > 90
--    OR longitude < -180 OR longitude > 180;
