ALTER TABLE sys_task
    ADD INDEX idx_task_workspace (assignee_id, status, due_date, deleted_at),
    ADD INDEX idx_task_project_updated (project_id, updated_at, deleted_at);

CREATE TABLE pm_risk_action (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    owner_user_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    response_plan VARCHAR(2000),
    due_date DATE,
    reason VARCHAR(1000),
    created_by BIGINT NOT NULL,
    updated_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_action_project_task (project_id, task_id),
    INDEX idx_risk_action_owner_status (owner_user_id, status),
    INDEX idx_risk_action_project_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务风险人工处理状态';

CREATE TABLE pm_risk_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    action_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    actor_id BIGINT,
    event_type VARCHAR(40) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20),
    score INT,
    level VARCHAR(20),
    note VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_risk_event_task_created (task_id, created_at),
    INDEX idx_risk_event_action_created (action_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险处理与状态变化事件';
