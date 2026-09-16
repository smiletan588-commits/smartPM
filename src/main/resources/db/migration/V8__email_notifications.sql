ALTER TABLE sys_user
    ADD COLUMN email VARCHAR(255) NULL AFTER nickname,
    ADD COLUMN email_verified_at DATETIME NULL AFTER email,
    ADD UNIQUE KEY uk_user_email (email);

CREATE TABLE pm_notification_preference (
    user_id BIGINT NOT NULL,
    email_enabled TINYINT(1) NOT NULL DEFAULT 0,
    assignment_enabled TINYINT(1) NOT NULL DEFAULT 1,
    mention_enabled TINYINT(1) NOT NULL DEFAULT 1,
    deadline_enabled TINYINT(1) NOT NULL DEFAULT 1,
    risk_enabled TINYINT(1) NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户邮件通知偏好';

CREATE TABLE pm_email_verification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_verification_token (token_hash),
    INDEX idx_email_verification_user (user_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱验证令牌';

CREATE TABLE pm_email_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at DATETIME,
    dedupe_key VARCHAR(190),
    last_error VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_outbox_dedupe (dedupe_key),
    INDEX idx_email_outbox_pending (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可靠邮件发送队列';
