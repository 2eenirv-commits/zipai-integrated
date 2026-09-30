CREATE TABLE IF NOT EXISTS admin_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_user_id BIGINT NULL,
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(80) NULL,
    details VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_admin_audit_created (created_at),
    KEY idx_admin_audit_admin_created (admin_user_id, created_at),
    CONSTRAINT fk_admin_audit_user
        FOREIGN KEY (admin_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
