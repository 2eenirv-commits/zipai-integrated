-- ZipAI 송성근 계정보안 컬럼 1회 적용용 SQL
-- 대상 DB: zipai
-- 주의: 아래 컬럼이 이미 존재하면 해당 ALTER 문은 실행하지 마세요.

USE zipai;

ALTER TABLE users
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD COLUMN locked_until DATETIME(6) NULL;

ALTER TABLE users
    ADD COLUMN email_verified_at DATETIME(6) NULL;

ALTER TABLE users
    ADD COLUMN phone_verified_at DATETIME(6) NULL;

ALTER TABLE users
    ADD COLUMN deleted_at DATETIME(6) NULL;

-- 확인
SHOW COLUMNS FROM users;
