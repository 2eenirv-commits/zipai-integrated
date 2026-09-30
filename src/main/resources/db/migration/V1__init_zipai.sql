-- ZipAI Flyway baseline migration
-- Version: 1
-- Purpose: make Flyway the single source of truth for the current ZipAI schema.
-- Existing non-empty databases are baselined at version 0 and then this V1 is applied.
-- CREATE TABLE IF NOT EXISTS / upsert statements make the first adoption idempotent.

CREATE TABLE IF NOT EXISTS room_visit (
    visit_id BIGINT NOT NULL AUTO_INCREMENT,
    room_id VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    visit_date DATE NOT NULL,
    visit_time TIME NOT NULL,
    phone VARCHAR(30) NOT NULL,
    question VARCHAR(1000) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (visit_id),
    KEY idx_room_visit_slot (room_id, visit_date, visit_time, status),
    KEY idx_room_visit_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS room_offer (
    offer_id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    district VARCHAR(120) NOT NULL,
    deposit BIGINT NOT NULL DEFAULT 0,
    monthly BIGINT NOT NULL DEFAULT 0,
    maintenance BIGINT NOT NULL DEFAULT 0,
    contract_end DATE NOT NULL,
    move_in DATE NOT NULL,
    available_time VARCHAR(200) NOT NULL,
    agreement VARCHAR(60) NOT NULL,
    description VARCHAR(2000) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ready',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (offer_id),
    KEY idx_room_offer_move_in (move_in),
    KEY idx_room_offer_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lifestyle_area (
    area_id BIGINT NOT NULL AUTO_INCREMENT,
    area_code VARCHAR(30) NOT NULL,
    sido VARCHAR(50) NOT NULL,
    sigungu VARCHAR(100) NOT NULL,
    dong VARCHAR(100) NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (area_id),
    UNIQUE KEY uk_lifestyle_area_code (area_code),
    KEY idx_lifestyle_area_region (sido, sigungu, dong),
    KEY idx_lifestyle_area_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lifestyle_score (
    score_id BIGINT NOT NULL AUTO_INCREMENT,
    area_id BIGINT NOT NULL,
    transport_score INT NULL,
    convenience_score INT NULL,
    medical_score INT NULL,
    education_score INT NULL,
    park_score INT NULL,
    safety_score INT NULL,
    commercial_score INT NULL,
    quiet_score INT NULL,
    cost_score INT NULL,
    source_name VARCHAR(200) NULL,
    source_date DATE NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (score_id),
    UNIQUE KEY uk_lifestyle_score_area_date (area_id, source_date),
    KEY idx_lifestyle_score_area (area_id),
    CONSTRAINT fk_lifestyle_score_area
        FOREIGN KEY (area_id)
        REFERENCES lifestyle_area(area_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_lifestyle_transport CHECK (transport_score IS NULL OR transport_score BETWEEN 0 AND 100),
    CONSTRAINT chk_lifestyle_convenience CHECK (convenience_score IS NULL OR convenience_score BETWEEN 0 AND 100),
    CONSTRAINT chk_lifestyle_medical CHECK (medical_score IS NULL OR medical_score BETWEEN 0 AND 100),
    CONSTRAINT chk_lifestyle_education CHECK (education_score IS NULL OR education_score BETWEEN 0 AND 100),
    CONSTRAINT chk_lifestyle_park CHECK (park_score IS NULL OR park_score BETWEEN 0 AND 100),
    CONSTRAINT chk_lifestyle_safety CHECK (safety_score IS NULL OR safety_score BETWEEN 0 AND 100),
    CONSTRAINT chk_lifestyle_commercial CHECK (commercial_score IS NULL OR commercial_score BETWEEN 0 AND 100),
    CONSTRAINT chk_lifestyle_quiet CHECK (quiet_score IS NULL OR quiet_score BETWEEN 0 AND 100),
    CONSTRAINT chk_lifestyle_cost CHECK (cost_score IS NULL OR cost_score BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Lifestyle 3차 지역 선택 범위 확장용 행정구역 카탈로그.
-- 점수 없는 지역도 선택목록에는 표시하며, 공식 점수 데이터는 후속 수집/정규화 단계에서 채운다.
INSERT INTO lifestyle_area (area_code, sido, sigungu, dong, latitude, longitude, active)
VALUES
    ('GG-SUWON', '경기도', '수원시', NULL, NULL, NULL, TRUE),
    ('GG-SEONGNAM', '경기도', '성남시', NULL, NULL, NULL, TRUE),
    ('GG-GOYANG', '경기도', '고양시', NULL, NULL, NULL, TRUE),
    ('GG-YONGIN', '경기도', '용인시', NULL, NULL, NULL, TRUE),
    ('GG-BUCHEON', '경기도', '부천시', NULL, NULL, NULL, TRUE),
    ('GG-ANSAN', '경기도', '안산시', NULL, NULL, NULL, TRUE),
    ('GG-ANYANG', '경기도', '안양시', NULL, NULL, NULL, TRUE),
    ('GG-NAMYANGJU', '경기도', '남양주시', NULL, NULL, NULL, TRUE),
    ('GG-HWASEONG', '경기도', '화성시', NULL, NULL, NULL, TRUE),
    ('GG-PYEONGTAEK', '경기도', '평택시', NULL, NULL, NULL, TRUE),
    ('GG-UIJEONGBU', '경기도', '의정부시', NULL, NULL, NULL, TRUE),
    ('GG-SIHEUNG', '경기도', '시흥시', NULL, NULL, NULL, TRUE),
    ('GG-PAJU', '경기도', '파주시', NULL, NULL, NULL, TRUE),
    ('GG-GIMPO', '경기도', '김포시', NULL, NULL, NULL, TRUE),
    ('GG-GWANGMYEONG', '경기도', '광명시', NULL, NULL, NULL, TRUE),
    ('GG-GWANGJU', '경기도', '광주시', NULL, NULL, NULL, TRUE),
    ('GG-GUNPO', '경기도', '군포시', NULL, NULL, NULL, TRUE),
    ('GG-HANAM', '경기도', '하남시', NULL, NULL, NULL, TRUE),
    ('GG-OSAN', '경기도', '오산시', NULL, NULL, NULL, TRUE),
    ('GG-YANGJU', '경기도', '양주시', NULL, NULL, NULL, TRUE),
    ('GG-ICHEON', '경기도', '이천시', NULL, NULL, NULL, TRUE),
    ('GG-GURI', '경기도', '구리시', NULL, NULL, NULL, TRUE),
    ('GG-ANSEONG', '경기도', '안성시', NULL, NULL, NULL, TRUE),
    ('GG-POCHEON', '경기도', '포천시', NULL, NULL, NULL, TRUE),
    ('GG-UIWANG', '경기도', '의왕시', NULL, NULL, NULL, TRUE),
    ('GG-YEOJU', '경기도', '여주시', NULL, NULL, NULL, TRUE),
    ('GG-DONGDUCHEON', '경기도', '동두천시', NULL, NULL, NULL, TRUE),
    ('GG-GWACHEON', '경기도', '과천시', NULL, NULL, NULL, TRUE),
    ('GG-YANGPYEONG', '경기도', '양평군', NULL, NULL, NULL, TRUE),
    ('GG-GAPYEONG', '경기도', '가평군', NULL, NULL, NULL, TRUE),
    ('GG-YEONCHEON', '경기도', '연천군', NULL, NULL, NULL, TRUE),
    ('SEO-JONGNO', '서울특별시', '종로구', NULL, NULL, NULL, TRUE),
    ('SEO-JUNG', '서울특별시', '중구', NULL, NULL, NULL, TRUE),
    ('SEO-YONGSAN', '서울특별시', '용산구', NULL, NULL, NULL, TRUE),
    ('SEO-SEONGDONG', '서울특별시', '성동구', NULL, NULL, NULL, TRUE),
    ('SEO-GWANGJIN', '서울특별시', '광진구', NULL, NULL, NULL, TRUE),
    ('SEO-DONGDAEMUN', '서울특별시', '동대문구', NULL, NULL, NULL, TRUE),
    ('SEO-JUNGNANG', '서울특별시', '중랑구', NULL, NULL, NULL, TRUE),
    ('SEO-SEONGBUK', '서울특별시', '성북구', NULL, NULL, NULL, TRUE),
    ('SEO-GANGBUK', '서울특별시', '강북구', NULL, NULL, NULL, TRUE),
    ('SEO-DOBONG', '서울특별시', '도봉구', NULL, NULL, NULL, TRUE),
    ('SEO-NOWON', '서울특별시', '노원구', NULL, NULL, NULL, TRUE),
    ('SEO-EUNPYEONG', '서울특별시', '은평구', NULL, NULL, NULL, TRUE),
    ('SEO-SEODAEMUN', '서울특별시', '서대문구', NULL, NULL, NULL, TRUE),
    ('SEO-MAPO', '서울특별시', '마포구', NULL, NULL, NULL, TRUE),
    ('SEO-YANGCHEON', '서울특별시', '양천구', NULL, NULL, NULL, TRUE),
    ('SEO-GANGSEO', '서울특별시', '강서구', NULL, NULL, NULL, TRUE),
    ('SEO-GURO', '서울특별시', '구로구', NULL, NULL, NULL, TRUE),
    ('SEO-GEUMCHEON', '서울특별시', '금천구', NULL, NULL, NULL, TRUE),
    ('SEO-YEONGDEUNGPO', '서울특별시', '영등포구', NULL, NULL, NULL, TRUE),
    ('SEO-DONGJAK', '서울특별시', '동작구', NULL, NULL, NULL, TRUE),
    ('SEO-GWANAK', '서울특별시', '관악구', NULL, NULL, NULL, TRUE),
    ('SEO-SEOCHO', '서울특별시', '서초구', NULL, NULL, NULL, TRUE),
    ('SEO-GANGNAM', '서울특별시', '강남구', NULL, NULL, NULL, TRUE),
    ('SEO-SONGPA', '서울특별시', '송파구', NULL, NULL, NULL, TRUE),
    ('SEO-GANGDONG', '서울특별시', '강동구', NULL, NULL, NULL, TRUE)
ON DUPLICATE KEY UPDATE
    sido = VALUES(sido), sigungu = VALUES(sigungu), dong = VALUES(dong), active = VALUES(active);

-- ============================================================
-- Kim member merge: Spring Data JDBC auth / community / finance
-- Added 2026-09-09. Existing Happy Housing / Lifestyle tables remain unchanged.
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(20) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(11) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'member',
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until DATETIME(6) NULL,
    email_verified_at DATETIME(6) NULL,
    phone_verified_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS community_posts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    author_id BIGINT NOT NULL,
    category VARCHAR(20) NOT NULL,
    title VARCHAR(60) NOT NULL,
    content TEXT NOT NULL,
    area VARCHAR(30) NOT NULL DEFAULT '',
    rating INT NOT NULL DEFAULT 0,
    views BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_community_posts_created (created_at),
    KEY idx_community_posts_author (author_id),
    CONSTRAINT fk_community_posts_author
        FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS community_post_likes (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_community_likes_post
        FOREIGN KEY (post_id) REFERENCES community_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_community_likes_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS community_comments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_community_comments_post_created (post_id, created_at),
    CONSTRAINT fk_community_comments_post
        FOREIGN KEY (post_id) REFERENCES community_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_community_comments_author
        FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS community_post_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL DEFAULT '',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_community_post_user_report (post_id, user_id),
    KEY idx_community_reports_post (post_id),
    CONSTRAINT fk_community_reports_post
        FOREIGN KEY (post_id) REFERENCES community_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_community_reports_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS community_post_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    image_url VARCHAR(550) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_community_images_post (post_id),
    CONSTRAINT fk_community_images_post
        FOREIGN KEY (post_id) REFERENCES community_posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS finance_policies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    limit_info VARCHAR(255) NOT NULL,
    rate_info VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_finance_policies_name (name),
    KEY idx_finance_category_target (category, target_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO finance_policies
    (category, name, target_type, limit_info, rate_info, description, created_at, updated_at)
VALUES
    ('purchase', '내집마련 디딤돌 대출', 'general', '최대 2.5억원 (생애최초 3억)', '연 2.85% ~ 4.15%', '부부합산 연소득 6천만원 이하(생애최초는 7천만원 이하), 순자산가액 5.11억원 이하 무주택 세대주 대상 주택 구입자금 대출', NOW(6), NOW(6)),
    ('purchase', '신혼부부 전용 구입자금', 'newlywed', '최대 4억원', '연 2.15% ~ 3.25%', '생애최초로 주택을 구입하는 신혼부부(부부합산 연소득 8.5천만원 이하), 순자산가액 5.11억원 이하 대상 주택 구입자금 대출', NOW(6), NOW(6)),
    ('purchase', '청년 주택드림 대출', 'youth', '최대 6억원 (분양가 80% 이내)', '최저 연 2.2% ~ 4.15%', '청년 주택드림 청약통장 가입 기간 1년 이상, 납입 금액 1천만원 이상이며 만 39세 이하 청년 대상 저리 분양자금 대출', NOW(6), NOW(6)),
    ('jeonse', '버팀목 전세자금대출', 'general', '수도권 1.2억원 / 비수도권 8천만원 이내', '연 1.90% ~ 3.30%', '근로자 및 서민의 주거안정을 위한 전세자금 대출 (소득 5천만원 이하, 자산 3.45억원 이하 무주택 세대주)', NOW(6), NOW(6)),
    ('jeonse', '신혼부부 전용 전세자금', 'newlywed', '수도권 최대 3억원 / 비수도권 2억원 이내', '연 1.50% ~ 2.70%', '혼인 7년 이내 신혼부부 또는 3개월 이내 결혼 예정자를 위한 초저금리 전세자금 대출 (부부합산 연소득 7.5천만원 이하)', NOW(6), NOW(6)),
    ('jeonse', '청년 전용 버팀목 전세자금대출', 'youth', '최대 2억원 (임차보증금의 80% 이내)', '연 1.80% ~ 2.70%', '만 19세 이상 ~ 만 34세 이하 청년 대상 전용 저리 전세자금 대출 (연소득 5천만원 이하, 순자산가액 3.45억원 이하)', NOW(6), NOW(6)),
    ('monthly', '주거안정 월세대출', 'general', '매월 최대 40만원 (최대 960만원 한도)', '우대형 연 1.3% / 일반형 연 1.8%', '주거취약계층(우대형) 또는 자립 준비중인 청년 및 일반 무주택자 대상 저리 월세대출 지원', NOW(6), NOW(6)),
    ('monthly', '청년 전용 보증부월세 대출', 'youth', '보증금 최대 5천만원 + 월세 최대 월 50만원 (보증금의 80% 이내)', '보증금 연 1.3% / 월세 최저 연 0% (무이자 혜택)', '만 19세 이상 ~ 34세 이하 청년을 위한 보증금 대출과 월세 대출을 결합한 특화 상품 (소득 5천만원 이하)', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE
    category = VALUES(category),
    target_type = VALUES(target_type),
    limit_info = VALUES(limit_info),
    rate_info = VALUES(rate_info),
    description = VALUES(description),
    updated_at = VALUES(updated_at);

-- ============================================================
-- Happy Housing core schema (previously created outside schema.sql)
-- ============================================================

CREATE TABLE IF NOT EXISTS eligibility_rule (
    rule_id BIGINT NOT NULL AUTO_INCREMENT,
    applicant_type VARCHAR(30) NOT NULL,
    rule_year INT NOT NULL,
    min_age INT NULL,
    max_age INT NULL,
    income_limit BIGINT NULL,
    asset_limit BIGINT NULL,
    car_limit BIGINT NULL,
    homeless_required BOOLEAN NOT NULL DEFAULT TRUE,
    category_required BOOLEAN NOT NULL DEFAULT TRUE,
    connection_required BOOLEAN NOT NULL DEFAULT FALSE,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (rule_id),
    KEY idx_eligibility_rule_lookup (applicant_type, active, effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Lifestyle property schema (previously maintained in sql/*.sql)
-- ============================================================

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

CREATE TABLE IF NOT EXISTS property_image (
    image_id BIGINT NOT NULL AUTO_INCREMENT,
    property_id BIGINT NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    representative BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (image_id),
    UNIQUE KEY uk_property_image_stored_name (stored_name),
    KEY idx_property_image_property (property_id, sort_order),
    CONSTRAINT fk_property_image_property
        FOREIGN KEY (property_id) REFERENCES lifestyle_property(property_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- Happy Housing crawler schema (previously created defensively by Python)
-- ============================================================

CREATE TABLE IF NOT EXISTS housing_notice (
    notice_id BIGINT NOT NULL AUTO_INCREMENT,
    pan_id VARCHAR(40) NOT NULL,
    source VARCHAR(100) NULL,
    title VARCHAR(500) NOT NULL,
    region VARCHAR(100) NULL,
    notice_date DATE NULL,
    posting_date DATE NULL,
    closing_date DATE NULL,
    status VARCHAR(100) NULL,
    housing_type VARCHAR(100) NULL,
    ccr_cnnt_sys_ds_cd VARCHAR(30) NULL,
    upp_ais_tp_cd VARCHAR(30) NULL,
    ais_tp_cd VARCHAR(30) NULL,
    pdf_file_id VARCHAR(80) NULL,
    pdf_file_name VARCHAR(700) NULL,
    hwpx_file_id VARCHAR(80) NULL,
    hwpx_file_name VARCHAR(700) NULL,
    pdf_text_path VARCHAR(1200) NULL,
    detail_endpoint VARCHAR(1200) NULL,
    crawled_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (notice_id),
    UNIQUE KEY uk_housing_notice_pan_id (pan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS housing_notice_rule (
    rule_id BIGINT NOT NULL AUTO_INCREMENT,
    notice_id BIGINT NOT NULL,
    pan_id VARCHAR(40) NOT NULL,
    applicant_type VARCHAR(60) NOT NULL,
    source_pdf VARCHAR(700) NULL,
    source_pdf_hash CHAR(64) NOT NULL,
    validation_status VARCHAR(40) NULL,
    rule_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (rule_id),
    UNIQUE KEY uk_notice_rule (notice_id, applicant_type, source_pdf_hash),
    KEY idx_notice_rule_pan_type (pan_id, applicant_type),
    CONSTRAINT fk_housing_notice_rule_notice
        FOREIGN KEY (notice_id) REFERENCES housing_notice(notice_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crawl_history (
    crawl_id BIGINT NOT NULL AUTO_INCREMENT,
    crawler_name VARCHAR(100) NOT NULL DEFAULT 'happy_housing_crawler',
    started_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    status VARCHAR(30) NOT NULL,
    notice_count INT NOT NULL DEFAULT 0,
    rule_count INT NOT NULL DEFAULT 0,
    parsed_rule_count INT NOT NULL DEFAULT 0,
    validation_rule_count INT NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    raw_json_path VARCHAR(1200) NULL,
    processed_json_path VARCHAR(1200) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (crawl_id),
    KEY idx_crawl_history_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
