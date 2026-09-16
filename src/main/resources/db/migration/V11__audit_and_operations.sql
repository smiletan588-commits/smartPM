CREATE TABLE pm_audit_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_id BIGINT,
    project_id BIGINT,
    category VARCHAR(40) NOT NULL,
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(40),
    target_id BIGINT,
    summary VARCHAR(500) NOT NULL,
    metadata JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_audit_project (project_id, created_at),
    INDEX idx_audit_actor (actor_id, created_at),
    INDEX idx_audit_category (category, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理操作审计';

CREATE TABLE pm_login_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    username VARCHAR(64) NOT NULL,
    success TINYINT(1) NOT NULL,
    reason VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_login_event_user (user_id, created_at),
    INDEX idx_login_event_failure (success, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录安全事件';

CREATE TABLE pm_system_operation_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    source VARCHAR(80),
    message VARCHAR(1000) NOT NULL,
    resolved_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_system_operation_open (resolved_at, severity, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统运营事件';
