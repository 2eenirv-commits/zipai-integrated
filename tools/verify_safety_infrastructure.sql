USE zipai;
SELECT facility_type, COUNT(*) AS cnt FROM safe_infrastructure GROUP BY facility_type ORDER BY facility_type;
SELECT CASE WHEN address LIKE '서울특별시%' THEN '서울특별시' WHEN address LIKE '경기도%' THEN '경기도' ELSE '기타' END AS region, facility_type, COUNT(*) AS cnt FROM safe_infrastructure GROUP BY region, facility_type ORDER BY region, facility_type;
