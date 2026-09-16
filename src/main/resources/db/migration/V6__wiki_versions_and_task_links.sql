CREATE TABLE pm_wiki_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    wiki_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content LONGTEXT,
    editor_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wiki_version (wiki_id, version_no),
    INDEX idx_wiki_version_created (wiki_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki 不可变版本';

CREATE TABLE pm_wiki_task (
    wiki_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (wiki_id, task_id),
    INDEX idx_wiki_task_task (task_id),
    INDEX idx_wiki_task_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki 与任务关联';
