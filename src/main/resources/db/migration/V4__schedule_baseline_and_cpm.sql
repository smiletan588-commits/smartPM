CREATE TABLE pm_schedule_baseline (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    project_start_date DATE,
    project_finish_date DATE,
    duration_days INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_baseline_project_name (project_id, name),
    INDEX idx_schedule_baseline_project_created (project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目排期基线';

CREATE TABLE pm_schedule_baseline_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    baseline_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    task_title VARCHAR(255) NOT NULL,
    planned_start_date DATE NOT NULL,
    planned_finish_date DATE NOT NULL,
    duration_days INT NOT NULL,
    predecessor_ids VARCHAR(2000),
    critical TINYINT(1) NOT NULL DEFAULT 0,
    total_slack_days INT NOT NULL DEFAULT 0,
    date_source VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_baseline_task (baseline_id, task_id),
    INDEX idx_schedule_baseline_item_baseline (baseline_id),
    INDEX idx_schedule_baseline_item_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目排期基线任务快照';
