-- ZipAI Lifestyle 2차 기능검증용 시연 데이터 1회 정리
-- lifestyle_score -> lifestyle_area는 ON DELETE CASCADE이므로
-- area 삭제 시 연결 score도 함께 삭제됩니다.
--
-- 안전장치:
-- source_name이 정확히 아래 문자열인 area만 삭제합니다.

START TRANSACTION;

DELETE a
FROM lifestyle_area a
JOIN lifestyle_score s
  ON s.area_id = a.area_id
WHERE s.source_name = 'ZipAI Lifestyle 2차 기능검증용 시연 데이터';

COMMIT;

-- 확인
SELECT COUNT(*) AS demo_rows_remaining
FROM lifestyle_score
WHERE source_name = 'ZipAI Lifestyle 2차 기능검증용 시연 데이터';

SELECT COUNT(*) AS gyeonggi_area_count
FROM lifestyle_area
WHERE sido = '경기도';
