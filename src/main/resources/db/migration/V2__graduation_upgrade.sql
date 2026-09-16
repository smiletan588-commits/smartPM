ALTER TABLE sys_task
    ADD COLUMN completed_at DATETIME NULL AFTER updated_at,
    ADD COLUMN ai_generated TINYINT(1) NOT NULL DEFAULT 0 AFTER completed_at;

CREATE TABLE pm_task_dependency (
    task_id BIGINT NOT NULL,
    prerequisite_task_id BIGINT NOT NULL,
    PRIMARY KEY (task_id, prerequisite_task_id),
    INDEX idx_prerequisite (prerequisite_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务前置依赖';

CREATE TABLE pm_milestone_task (
    milestone_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    PRIMARY KEY (milestone_id, task_id),
    INDEX idx_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='里程碑任务关联';

CREATE TABLE pm_task_comment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    mentioned_user_ids VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME,
    PRIMARY KEY (id), INDEX idx_task_created (task_id, created_at), INDEX idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务评论';

CREATE TABLE pm_task_activity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    task_id BIGINT,
    actor_id BIGINT,
    action_type VARCHAR(40) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    before_json JSON,
    after_json JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), INDEX idx_task_created (task_id, created_at), INDEX idx_project_created (project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目任务活动';

CREATE TABLE pm_notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    project_id BIGINT,
    task_id BIGINT,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(160) NOT NULL,
    content VARCHAR(500),
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    dedupe_key VARCHAR(190),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME,
    PRIMARY KEY (id), UNIQUE KEY uk_notification_dedupe (dedupe_key),
    INDEX idx_user_read_created (user_id, is_read, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知';

CREATE TABLE pm_ai_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    project_id BIGINT,
    task_id BIGINT,
    scene VARCHAR(40) NOT NULL,
    model VARCHAR(80),
    prompt_version VARCHAR(40) NOT NULL,
    duration_ms BIGINT,
    success TINYINT(1),
    generated_count INT NOT NULL DEFAULT 0,
    applied TINYINT(1) NOT NULL DEFAULT 0,
    modified_count INT NOT NULL DEFAULT 0,
    rating INT,
    feedback VARCHAR(500),
    error_message VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,
    PRIMARY KEY (id), INDEX idx_user_created (user_id, created_at), INDEX idx_project_scene (project_id, scene)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 使用与评价记录';

INSERT IGNORE INTO pm_task_dependency (task_id, prerequisite_task_id)
SELECT t.id, CAST(j.dependency_id AS UNSIGNED)
FROM sys_task t
JOIN JSON_TABLE(
    CONCAT('["', REPLACE(REPLACE(t.dependency_ids, ' ', ''), ',', '","'), '"]'),
    '$[*]' COLUMNS(dependency_id VARCHAR(32) PATH '$')
) j
WHERE t.dependency_ids IS NOT NULL AND TRIM(t.dependency_ids) <> '' AND j.dependency_id REGEXP '^[0-9]+$';

INSERT IGNORE INTO pm_milestone_task (milestone_id, task_id)
SELECT m.id, CAST(j.task_id AS UNSIGNED)
FROM pm_project_milestone m
JOIN JSON_TABLE(
    CONCAT('["', REPLACE(REPLACE(m.task_ids, ' ', ''), ',', '","'), '"]'),
    '$[*]' COLUMNS(task_id VARCHAR(32) PATH '$')
) j
WHERE m.task_ids IS NOT NULL AND TRIM(m.task_ids) <> '' AND j.task_id REGEXP '^[0-9]+$';
