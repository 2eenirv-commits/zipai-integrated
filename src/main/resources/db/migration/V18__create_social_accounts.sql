CREATE TABLE IF NOT EXISTS social_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(254) NULL,
    display_name VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_social_accounts_provider_user (provider, provider_user_id),
    UNIQUE KEY uk_social_accounts_user_provider (user_id, provider),
    KEY idx_social_accounts_user (user_id),
    CONSTRAINT fk_social_accounts_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
