package com.smartpm.service.impl;

import com.smartpm.common.utils.UserHolder;
import com.smartpm.entity.User;
import com.smartpm.mapper.UserMapper;
import com.smartpm.service.RoleWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RoleWorkspaceServiceImpl implements RoleWorkspaceService {
    private final JdbcTemplate jdbc;
    private final UserMapper userMapper;

    @Override
    public Map<String, Object> roleView() {
        Long userId = UserHolder.getUserId();
        User user = userMapper.selectById(userId);
        String identity = resolveIdentity(user, userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("identity", identity);
        result.put("title", title(identity));
        result.put("subtitle", subtitle(identity));
        result.put("primaryAction", primaryAction(identity));
        result.put("sections", sections(identity, userId));
        return result;
    }

    private List<Map<String, Object>> sections(String identity, Long userId) {
        String access = " EXISTS(SELECT 1 FROM sys_project p LEFT JOIN pm_project_member pm ON pm.project_id=p.id AND pm.user_id=? WHERE p.id=t.project_id AND p.deleted_at IS NULL AND (p.creator_id=? OR pm.user_id=?)) ";
        List<Map<String, Object>> sections = new ArrayList<>();
        if ("SYSTEM_ADMIN".equals(identity)) {
            sections.add(section("users", "系统用户", count("SELECT COUNT(*) FROM sys_user"), "/admin/users"));
            sections.add(section("mail", "待发送邮件", count("SELECT COUNT(*) FROM pm_email_outbox WHERE status='PENDING'"), "/admin/users"));
            sections.add(section("failures", "24 小时登录失败", count("SELECT COUNT(*) FROM pm_login_event WHERE success=0 AND created_at>=DATE_SUB(NOW(),INTERVAL 24 HOUR)"), "/admin/users"));
        } else if ("VIEWER".equals(identity)) {
            sections.add(section("projects", "可查看项目", count("SELECT COUNT(*) FROM pm_project_member WHERE user_id=?", userId), "/dashboard"));
            sections.add(section("milestones", "近期里程碑", count("SELECT COUNT(*) FROM pm_project_milestone m JOIN pm_project_member pm ON pm.project_id=m.project_id WHERE pm.user_id=? AND m.status<>'COMPLETED' AND m.target_date BETWEEN ? AND ?", userId, LocalDate.now(), LocalDate.now().plusDays(14)), "/dashboard?roleScope=milestones"));
            sections.add(section("risks", "待处理风险", count("SELECT COUNT(*) FROM pm_risk_action r JOIN pm_project_member pm ON pm.project_id=r.project_id WHERE pm.user_id=? AND r.status<>'RESOLVED'", userId), "/dashboard?roleScope=risks"));
        } else if ("PRODUCT_MANAGER".equals(identity)) {
            sections.add(section("drafts", "待完善产品成果", count("SELECT COUNT(*) FROM pm_product_artifact a WHERE a.status IN ('DRAFT','IN_REVIEW') AND EXISTS(SELECT 1 FROM sys_project p LEFT JOIN pm_project_member pm ON pm.project_id=p.id AND pm.user_id=? WHERE p.id=a.project_id AND (p.creator_id=? OR pm.user_id=?))", userId, userId, userId), "/dashboard?roleScope=product-drafts"));
            sections.add(section("decisions", "待确认产品决策", count("SELECT COUNT(*) FROM pm_product_decision d WHERE d.status='OPEN' AND EXISTS(SELECT 1 FROM sys_project p LEFT JOIN pm_project_member pm ON pm.project_id=p.id AND pm.user_id=? WHERE p.id=d.project_id AND (p.creator_id=? OR pm.user_id=?))", userId, userId, userId), "/dashboard?roleScope=decisions"));
            sections.add(section("requirements", "缺少验收标准", count("SELECT COUNT(*) FROM sys_task t WHERE t.status<>'DONE' AND t.deleted_at IS NULL AND (t.acceptance_criteria IS NULL OR t.acceptance_criteria='') AND " + access, userId, userId, userId), "/dashboard?roleScope=missing-acceptance"));
        } else if ("PROJECT_MANAGER".equals(identity)) {
            sections.add(section("overdue", "逾期任务", count("SELECT COUNT(*) FROM sys_task t WHERE t.status<>'DONE' AND t.due_date<? AND t.deleted_at IS NULL AND " + access, LocalDate.now(), userId, userId, userId), "/dashboard?scope=overdue"));
            sections.add(section("unassigned", "未分配任务", count("SELECT COUNT(*) FROM sys_task t WHERE t.status<>'DONE' AND t.assignee_id IS NULL AND t.deleted_at IS NULL AND " + access, userId, userId, userId), "/dashboard?roleScope=unassigned"));
            sections.add(section("milestones", "延期里程碑", count("SELECT COUNT(*) FROM pm_project_milestone m WHERE m.status<>'COMPLETED' AND m.target_date<? AND EXISTS(SELECT 1 FROM sys_project p LEFT JOIN pm_project_member pm ON pm.project_id=p.id AND pm.user_id=? WHERE p.id=m.project_id AND (p.creator_id=? OR pm.user_id=?))", LocalDate.now(), userId, userId, userId), "/dashboard?roleScope=milestones"));
        } else if ("QA_TESTER".equals(identity)) {
            sections.add(section("pending-review", "待验收", count("SELECT COUNT(*) FROM sys_task t WHERE t.acceptance_status IN ('PENDING','IN_REVIEW') AND t.deleted_at IS NULL AND " + access, userId, userId, userId), "/dashboard?roleScope=pending-review"));
            sections.add(section("regression", "待回归", count("SELECT COUNT(*) FROM sys_task t WHERE t.acceptance_status='REJECTED' AND t.deleted_at IS NULL AND " + access, userId, userId, userId), "/dashboard?roleScope=regression"));
        } else if ("UI_DESIGNER".equals(identity)) {
            sections.add(section("design", "我的设计任务", count("SELECT COUNT(*) FROM sys_task t WHERE t.assignee_id=? AND t.status<>'DONE' AND (FIND_IN_SET('DESIGN',t.tags)>0 OR t.recommended_role='UI_DESIGNER') AND t.deleted_at IS NULL", userId), "/dashboard?scope=mine&roleScope=design"));
            sections.add(section("design-review", "待设计评审", count("SELECT COUNT(*) FROM sys_task t WHERE t.acceptance_status IN ('PENDING','IN_REVIEW') AND (FIND_IN_SET('DESIGN',t.tags)>0 OR t.recommended_role='UI_DESIGNER') AND t.deleted_at IS NULL AND " + access, userId, userId, userId), "/dashboard?roleScope=design-review"));
        } else {
            sections.add(section("ready", "可以立即开始", count("SELECT COUNT(*) FROM sys_task t WHERE t.assignee_id=? AND t.status='TODO' AND t.deleted_at IS NULL AND NOT EXISTS(SELECT 1 FROM pm_task_dependency d JOIN sys_task p ON p.id=d.prerequisite_task_id WHERE d.task_id=t.id AND p.status<>'DONE')", userId), "/dashboard?scope=mine&roleScope=ready"));
            sections.add(section("active", "进行中的任务", count("SELECT COUNT(*) FROM sys_task WHERE assignee_id=? AND status='IN_PROGRESS' AND deleted_at IS NULL", userId), "/dashboard?scope=mine&status=IN_PROGRESS"));
            sections.add(section("comments", "待处理提醒", count("SELECT COUNT(*) FROM pm_notification WHERE user_id=? AND is_read=0", userId), "/dashboard?roleScope=notifications"));
        }
        return sections;
    }

    private int count(String sql, Object... args) { Integer value = jdbc.queryForObject(sql, Integer.class, args); return value == null ? 0 : value; }
    private Map<String, Object> section(String key, String label, int count, String route) { return Map.of("key", key, "label", label, "count", count, "route", route); }
    private String resolveIdentity(User user, Long userId) {
        if (user != null && "ADMIN".equals(user.getSystemRole())) return "SYSTEM_ADMIN";
        int accessible = count("SELECT COUNT(*) FROM pm_project_member WHERE user_id=?", userId)
                + count("SELECT COUNT(*) FROM sys_project WHERE creator_id=? AND deleted_at IS NULL", userId);
        int writable = count("SELECT COUNT(*) FROM pm_project_member WHERE user_id=? AND permission<>'VIEWER'", userId)
                + count("SELECT COUNT(*) FROM sys_project WHERE creator_id=? AND deleted_at IS NULL", userId);
        if (accessible > 0 && writable == 0) return "VIEWER";
        return user == null || user.getIdentity() == null ? "PROJECT_MANAGER" : user.getIdentity();
    }
    private String title(String identity) { return switch (identity) { case "SYSTEM_ADMIN" -> "系统运营工作台"; case "VIEWER" -> "项目简报工作台"; case "PRODUCT_MANAGER" -> "产品共创工作台"; case "PROJECT_MANAGER" -> "项目决策工作台"; case "QA_TESTER" -> "质量验收工作台"; case "UI_DESIGNER" -> "设计交付工作台"; default -> "我的执行工作台"; }; }
    private String subtitle(String identity) { return switch (identity) { case "SYSTEM_ADMIN" -> "关注账号安全、邮件队列和系统运行状态"; case "VIEWER" -> "只呈现项目结论、近期里程碑和主要风险"; case "PRODUCT_MANAGER" -> "从想法澄清到 PRD、用户故事与版本范围"; case "PROJECT_MANAGER" -> "聚焦延期、风险、容量和待决策事项"; case "QA_TESTER" -> "集中处理待验收、驳回与回归任务"; case "UI_DESIGNER" -> "跟踪设计任务、审阅和修改反馈"; default -> "只呈现现在可以推进的工作和真实阻塞"; }; }
    private String primaryAction(String identity) { return switch (identity) { case "PRODUCT_MANAGER" -> "OPEN_PRODUCT_LAB"; case "SYSTEM_ADMIN" -> "OPEN_SYSTEM_OPERATIONS"; default -> "OPEN_MY_TASKS"; }; }
}
