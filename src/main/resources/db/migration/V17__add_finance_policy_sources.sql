ALTER TABLE finance_policies
  ADD COLUMN source_name VARCHAR(100) NULL AFTER description;

ALTER TABLE finance_policies
  ADD COLUMN source_url VARCHAR(1000) NULL AFTER source_name;

ALTER TABLE finance_policies
  ADD COLUMN source_checked_at DATETIME(6) NULL AFTER source_url;

ALTER TABLE finance_policies
  ADD COLUMN source_hash VARCHAR(64) NULL AFTER source_checked_at;

ALTER TABLE finance_policies
  ADD COLUMN update_status VARCHAR(20) NOT NULL DEFAULT 'current' AFTER source_hash;

CREATE TABLE finance_policy_update_candidates (
  id BIGINT NOT NULL AUTO_INCREMENT,
  policy_id BIGINT NOT NULL,
  source_name VARCHAR(100) NOT NULL,
  source_url VARCHAR(1000) NOT NULL,
  source_hash VARCHAR(64) NOT NULL,
  snapshot_excerpt TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  detected_at DATETIME(6) NOT NULL,
  reviewed_at DATETIME(6) NULL,
  reviewed_by BIGINT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_finance_candidate_hash (policy_id, source_hash),
  KEY idx_finance_candidate_status (status, detected_at),
  CONSTRAINT fk_finance_candidate_policy FOREIGN KEY (policy_id) REFERENCES finance_policies(id) ON DELETE CASCADE,
  CONSTRAINT fk_finance_candidate_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT chk_finance_candidate_status CHECK (status IN ('pending', 'approved', 'rejected'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
