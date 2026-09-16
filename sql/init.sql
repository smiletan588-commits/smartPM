-- 创建数据库
CREATE DATABASE IF NOT EXISTS smartpm DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE smartpm;

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username    VARCHAR(64)  NOT NULL COMMENT '用户名',
    password    VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    nickname    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    identity    VARCHAR(50)  DEFAULT NULL COMMENT '专业身份: PROJECT_MANAGER/FRONTEND_DEV/BACKEND_DEV/QA_TESTER/UI_DESIGNER',
    system_role VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '系统权限: ADMIN/USER',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态: ACTIVE/DISABLED',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 项目表
CREATE TABLE IF NOT EXISTS sys_project (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '项目ID',
    name        VARCHAR(255) NOT NULL COMMENT '项目名称',
    description TEXT         DEFAULT NULL COMMENT '项目描述',
    invite_code VARCHAR(8)   DEFAULT NULL COMMENT '项目邀请码',
    creator_id  BIGINT       NOT NULL COMMENT '创建者ID',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at  DATETIME     DEFAULT NULL COMMENT '移入回收站时间',
    deleted_by  BIGINT       DEFAULT NULL COMMENT '删除人ID',
    PRIMARY KEY (id),
    INDEX idx_creator (creator_id),
    UNIQUE KEY uk_invite_code (invite_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目表';

-- 任务表
CREATE TABLE IF NOT EXISTS sys_task (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    parent_id   BIGINT       DEFAULT NULL COMMENT '父任务ID(NULL=主任务, 非NULL=子任务)',
    project_id  BIGINT       NOT NULL COMMENT '所属项目ID',
    title       VARCHAR(255) NOT NULL COMMENT '任务标题',
    description TEXT         DEFAULT NULL COMMENT '任务描述',
    status      VARCHAR(32)  NOT NULL DEFAULT 'TODO' COMMENT '状态: TODO/IN_PROGRESS/DONE',
    assignee_id BIGINT       DEFAULT NULL COMMENT '负责人ID',
    recommended_role VARCHAR(50) DEFAULT NULL COMMENT 'AI推荐角色',
    priority    VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH/MEDIUM/LOW',
    tags        VARCHAR(255) DEFAULT NULL COMMENT '任务标签代码，逗号分隔',
    creator_id  BIGINT       NOT NULL COMMENT '创建者ID',
    start_date  DATE         DEFAULT NULL COMMENT '开始日期',
    due_date    DATE         DEFAULT NULL COMMENT '截止日期',
    estimated_hours INT      DEFAULT NULL COMMENT '预计工时（小时）',
    actual_hours INT         DEFAULT NULL COMMENT '实际工时（小时）',
    dependency_ids VARCHAR(255) DEFAULT NULL COMMENT '前置任务ID，逗号分隔',
    acceptance_criteria TEXT    DEFAULT NULL COMMENT '任务验收标准',
    order_index INT          NOT NULL DEFAULT 0 COMMENT '排序权重',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at  DATETIME     DEFAULT NULL COMMENT '移入回收站时间',
    deleted_by  BIGINT       DEFAULT NULL COMMENT '删除人ID',
    PRIMARY KEY (id),
    INDEX idx_project (project_id),
    INDEX idx_assignee (assignee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务表';

-- 项目成员表
CREATE TABLE IF NOT EXISTS pm_project_member (
    id          BIGINT  NOT NULL AUTO_INCREMENT COMMENT '成员关联ID',
    project_id  BIGINT  NOT NULL COMMENT '项目ID',
    user_id     BIGINT  NOT NULL COMMENT '用户ID',
    identity    VARCHAR(50) DEFAULT NULL COMMENT '项目内身份',
    permission  VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT 'PROJECT_ADMIN/MEMBER/VIEWER',
    joined_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_user (project_id, user_id),
    INDEX idx_project (project_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目成员表';

-- 项目里程碑表
CREATE TABLE IF NOT EXISTS pm_project_milestone (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '里程碑ID',
    project_id  BIGINT       NOT NULL COMMENT '所属项目ID',
    name        VARCHAR(128) NOT NULL COMMENT '里程碑名称',
    description TEXT         DEFAULT NULL COMMENT '说明',
    target_date DATE         DEFAULT NULL COMMENT '目标日期',
    status      VARCHAR(16)  NOT NULL DEFAULT 'PLANNED' COMMENT 'PLANNED/COMPLETED',
    task_ids    VARCHAR(1000) DEFAULT NULL COMMENT '关联任务ID，逗号分隔',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目里程碑';

-- 任务附件和下载审计表。实际文件保存在后端 uploads/tasks 目录。
CREATE TABLE IF NOT EXISTS pm_task_attachment (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '附件ID',
    task_id       BIGINT       NOT NULL COMMENT '任务ID',
    project_id    BIGINT       NOT NULL COMMENT '项目ID',
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    stored_name   VARCHAR(255) NOT NULL COMMENT '服务器文件名',
    content_type  VARCHAR(128) DEFAULT NULL COMMENT '文件类型',
    size          BIGINT       NOT NULL COMMENT '文件大小（字节）',
    uploader_id   BIGINT       NOT NULL COMMENT '上传人ID',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at    DATETIME     DEFAULT NULL COMMENT '移入回收站时间',
    deleted_by    BIGINT       DEFAULT NULL COMMENT '删除人ID',
    PRIMARY KEY (id),
    INDEX idx_task (task_id),
    INDEX idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务附件';

CREATE TABLE IF NOT EXISTS pm_attachment_download_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    attachment_id BIGINT       NOT NULL COMMENT '附件ID',
    task_id       BIGINT       NOT NULL COMMENT '任务ID',
    project_id    BIGINT       NOT NULL COMMENT '项目ID',
    downloader_id BIGINT       NOT NULL COMMENT '下载人ID',
    downloaded_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_attachment (attachment_id),
    INDEX idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件下载记录';

CREATE TABLE IF NOT EXISTS pm_wiki (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文档ID',
    project_id  BIGINT       NOT NULL COMMENT '项目ID',
    title       VARCHAR(255) NOT NULL COMMENT '文档标题',
    content     LONGTEXT     DEFAULT NULL COMMENT '文档内容',
    creator_id  BIGINT       NOT NULL COMMENT '创建人ID',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at  DATETIME     DEFAULT NULL COMMENT '移入回收站时间',
    deleted_by  BIGINT       DEFAULT NULL COMMENT '删除人ID',
    PRIMARY KEY (id),
    INDEX idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目文档';

CREATE TABLE IF NOT EXISTS pm_schedule_baseline (
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

CREATE TABLE IF NOT EXISTS pm_schedule_baseline_item (
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
