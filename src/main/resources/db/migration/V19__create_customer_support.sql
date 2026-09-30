CREATE TABLE IF NOT EXISTS customer_inquiry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    category VARCHAR(30) NOT NULL,
    email VARCHAR(254) NOT NULL,
    title VARCHAR(60) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'received',
    answer VARCHAR(1500) NULL,
    answered_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_customer_inquiry_user_created (user_id, created_at),
    KEY idx_customer_inquiry_status_created (status, created_at),
    CONSTRAINT fk_customer_inquiry_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    notification_type VARCHAR(30) NOT NULL,
    title VARCHAR(100) NOT NULL,
    message VARCHAR(500) NOT NULL,
    target_url VARCHAR(300) NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_user_notification_user_read_created (user_id, is_read, created_at),
    CONSTRAINT fk_user_notification_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
