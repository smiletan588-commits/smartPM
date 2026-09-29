ALTER TABLE sys_task ADD COLUMN recommended_skill VARCHAR(100) NULL AFTER recommended_role;

CREATE TABLE pm_ai_planning_draft (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    creator_id BIGINT NOT NULL,
    operation_id BIGINT NULL,
    mode VARCHAR(16) NOT NULL,
    parent_task_id BIGINT NULL,
    input_json JSON NOT NULL,
    content_json JSON NOT NULL,
    version_no INT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    applied_at DATETIME NULL,
    PRIMARY KEY (id),
    INDEX idx_ai_draft_project_user (project_id, creator_id, updated_at),
    CONSTRAINT fk_ai_draft_project FOREIGN KEY (project_id) REFERENCES sys_project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI任务规划待审草稿';
