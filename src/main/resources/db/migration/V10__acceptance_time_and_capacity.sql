ALTER TABLE sys_task
    ADD COLUMN review_required TINYINT(1) NOT NULL DEFAULT 0 AFTER acceptance_criteria,
    ADD COLUMN acceptance_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED' AFTER review_required,
    ADD COLUMN acceptance_submitted_at DATETIME NULL AFTER acceptance_status;

CREATE TABLE pm_acceptance_checklist (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    checked TINYINT(1) NOT NULL DEFAULT 0,
    order_index INT NOT NULL DEFAULT 0,
    checked_by BIGINT,
    checked_at DATETIME,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_acceptance_checklist_task (task_id, order_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务验收清单';

CREATE TABLE pm_task_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    comment VARCHAR(2000),
    evidence_attachment_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_task_review_task (task_id, created_at),
    INDEX idx_task_review_project (project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务验收记录';

CREATE TABLE pm_time_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    hours DECIMAL(5,2) NOT NULL,
    note VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_time_entry_task (task_id, work_date),
    INDEX idx_time_entry_capacity (project_id, user_id, work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务工时日志';

CREATE TABLE pm_member_capacity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    weekly_hours DECIMAL(5,2) NOT NULL DEFAULT 40.00,
    effective_from DATE NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_capacity (project_id, user_id, effective_from),
    INDEX idx_member_capacity_current (project_id, user_id, effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目成员周容量';

CREATE TABLE pm_capacity_exception (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    exception_date DATE NOT NULL,
    available_hours DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    reason VARCHAR(500),
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_capacity_exception (project_id, user_id, exception_date),
    INDEX idx_capacity_exception_project (project_id, exception_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='容量及请假例外';
