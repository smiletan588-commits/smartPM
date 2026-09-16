-- 旧数据库没有独立完成时间；升级时以最后更新时间作为一次性基线估计。
-- 升级后的状态变更由业务层维护 completed_at，可用于真实完工趋势。
UPDATE sys_task
SET completed_at = updated_at
WHERE status = 'DONE' AND completed_at IS NULL;

CREATE INDEX idx_task_project_deleted_status
    ON sys_task (project_id, deleted_at, status);

CREATE INDEX idx_ai_operation_success_created
    ON pm_ai_operation_log (success, created_at);
