CREATE TABLE pm_project_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    owner_id BIGINT NOT NULL,
    source_project_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_template_owner_created (owner_id, created_at),
    INDEX idx_template_visibility (visibility)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目模板';

CREATE TABLE pm_project_template_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    source_key BIGINT NOT NULL,
    parent_source_key BIGINT,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    recommended_role VARCHAR(50),
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    tags VARCHAR(255),
    relative_start_day INT,
    duration_days INT NOT NULL DEFAULT 1,
    estimated_hours INT,
    acceptance_criteria TEXT,
    order_index INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_task_source (template_id, source_key),
    INDEX idx_template_task_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目模板任务';

CREATE TABLE pm_project_template_dependency (
    template_id BIGINT NOT NULL,
    task_source_key BIGINT NOT NULL,
    prerequisite_source_key BIGINT NOT NULL,
    PRIMARY KEY (template_id, task_source_key, prerequisite_source_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目模板任务依赖';

CREATE TABLE pm_project_template_milestone (
    id BIGINT NOT NULL AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    source_key BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    relative_target_day INT,
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_milestone_source (template_id, source_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目模板里程碑';

CREATE TABLE pm_project_template_milestone_task (
    template_id BIGINT NOT NULL,
    milestone_source_key BIGINT NOT NULL,
    task_source_key BIGINT NOT NULL,
    PRIMARY KEY (template_id, milestone_source_key, task_source_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板里程碑任务关联';

CREATE TABLE pm_task_recurrence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    source_task_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    assignee_id BIGINT,
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    tags VARCHAR(255),
    estimated_hours INT,
    acceptance_criteria TEXT,
    frequency VARCHAR(16) NOT NULL,
    interval_value INT NOT NULL DEFAULT 1,
    weekdays VARCHAR(32),
    day_of_month INT,
    start_date DATE NOT NULL,
    end_date DATE,
    due_offset_days INT NOT NULL DEFAULT 0,
    next_run_date DATE,
    last_period_key VARCHAR(40),
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_recurrence_source (source_task_id),
    INDEX idx_recurrence_due (active, next_run_date),
    INDEX idx_recurrence_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='周期任务规则';

CREATE TABLE pm_task_recurrence_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recurrence_id BIGINT NOT NULL,
    period_key VARCHAR(40) NOT NULL,
    generated_task_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_recurrence_period (recurrence_id, period_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='周期任务幂等执行记录';
