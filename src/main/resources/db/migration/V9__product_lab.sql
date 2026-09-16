CREATE TABLE pm_product_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    stage VARCHAR(32) NOT NULL DEFAULT 'IDEA',
    mode VARCHAR(40) NOT NULL DEFAULT 'IDEA_REFINEMENT',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_product_conversation_project (project_id, updated_at),
    INDEX idx_product_conversation_creator (created_by, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品共创会话';

CREATE TABLE pm_product_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETE',
    context_json JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_product_message_conversation (conversation_id, id),
    INDEX idx_product_message_project (project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品共创消息';

CREATE TABLE pm_product_artifact (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    conversation_id BIGINT,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version_no INT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    published_wiki_id BIGINT,
    applied_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_product_artifact_project (project_id, updated_at),
    INDEX idx_product_artifact_conversation (conversation_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品共创结构化成果';

CREATE TABLE pm_product_artifact_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    artifact_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    edited_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_artifact_version (artifact_id, version_no),
    INDEX idx_product_artifact_version_project (project_id, artifact_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品成果版本';

CREATE TABLE pm_product_decision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    conversation_id BIGINT,
    artifact_id BIGINT,
    title VARCHAR(255) NOT NULL,
    decision TEXT,
    rationale TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    owner_id BIGINT,
    due_date DATE,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_product_decision_project (project_id, status, due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品决策清单';
