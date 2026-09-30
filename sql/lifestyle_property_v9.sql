SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lifestyle_property (
    property_id BIGINT NOT NULL AUTO_INCREMENT,
    property_code VARCHAR(60) NOT NULL,
    sido VARCHAR(50) NOT NULL,
    sigungu VARCHAR(100) NOT NULL,
    dong VARCHAR(100) NULL,
    title VARCHAR(200) NOT NULL,
    deposit BIGINT NOT NULL DEFAULT 0,
    monthly_rent BIGINT NOT NULL DEFAULT 0,
    maintenance_fee BIGINT NOT NULL DEFAULT 0,
    available_time VARCHAR(200) NOT NULL,
    thumbnail_url VARCHAR(1000) NULL,
    photo_credit VARCHAR(200) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ready',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sample_data BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (property_id),
    UNIQUE KEY uk_lifestyle_property_code (property_code),
    KEY idx_lifestyle_property_region (sido, sigungu, active, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO lifestyle_property (
    property_code,sido,sigungu,dong,title,deposit,monthly_rent,maintenance_fee,
    available_time,thumbnail_url,photo_credit,status,active,sample_data
)
SELECT
    CONCAT('PROP-', a.area_code, '-', n.no),
    a.sido,a.sigungu,a.dong,
    CONCAT(a.sigungu, ' ',
        CASE n.no WHEN 1 THEN '채광 좋은 원룸'
                  WHEN 2 THEN '생활권 투룸'
                  WHEN 3 THEN '풀옵션 오피스텔'
                  ELSE '반려동물 협의 원룸' END),
    CASE n.no WHEN 1 THEN 500 WHEN 2 THEN 1000 WHEN 3 THEN 1500 ELSE 2000 END,
    CASE n.no WHEN 1 THEN 48 WHEN 2 THEN 55 WHEN 3 THEN 62 ELSE 68 END,
    CASE n.no WHEN 1 THEN 5 WHEN 2 THEN 7 WHEN 3 THEN 8 ELSE 6 END,
    CASE n.no WHEN 1 THEN '평일 19시 이후'
              WHEN 2 THEN '주말 10시~17시'
              WHEN 3 THEN '평일·주말 협의'
              ELSE '토요일 오후' END,
    CASE n.no
        WHEN 1 THEN 'https://images.unsplash.com/photo-1751945965597-71171ec7a458?auto=format&fit=crop&w=900&q=80'
        WHEN 2 THEN 'https://images.unsplash.com/photo-1677100091551-748c921e6609?auto=format&fit=crop&w=900&q=80'
        WHEN 3 THEN 'https://images.unsplash.com/photo-1663756915301-2ba688e078cf?auto=format&fit=crop&w=900&q=80'
        ELSE 'https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?auto=format&fit=crop&w=900&q=80'
    END,
    'Unsplash 개발용 샘플 사진','ready',TRUE,TRUE
FROM lifestyle_area a
CROSS JOIN (
    SELECT 1 AS no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
) n
WHERE a.active=TRUE
ON DUPLICATE KEY UPDATE
    sido=VALUES(sido),sigungu=VALUES(sigungu),dong=VALUES(dong),title=VALUES(title),
    deposit=VALUES(deposit),monthly_rent=VALUES(monthly_rent),
    maintenance_fee=VALUES(maintenance_fee),available_time=VALUES(available_time),
    thumbnail_url=VALUES(thumbnail_url),photo_credit=VALUES(photo_credit),
    status=VALUES(status),active=VALUES(active),sample_data=VALUES(sample_data);

SELECT sido, COUNT(*) AS property_count
FROM lifestyle_property WHERE active=TRUE
GROUP BY sido ORDER BY sido;

SELECT sigungu, COUNT(*) AS property_count
FROM lifestyle_property
WHERE sido='경기도' AND active=TRUE
GROUP BY sigungu ORDER BY sigungu;
