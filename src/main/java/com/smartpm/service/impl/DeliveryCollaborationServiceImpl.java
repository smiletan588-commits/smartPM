package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.dto.*;
import com.smartpm.entity.Project;
import com.smartpm.entity.ProjectMember;
import com.smartpm.entity.Task;
import com.smartpm.entity.User;
import com.smartpm.mapper.ProjectMemberMapper;
import com.smartpm.mapper.TaskMapper;
import com.smartpm.mapper.UserMapper;
import com.smartpm.service.AuditService;
import com.smartpm.service.DeliveryCollaborationService;
import com.smartpm.service.ProjectService;
import com.smartpm.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DeliveryCollaborationServiceImpl implements DeliveryCollaborationService {
    private static final Set<String> REVIEWER_IDENTITIES = Set.of("QA_TESTER", "PRODUCT_MANAGER", "UI_DESIGNER", "PROJECT_MANAGER");
    private final JdbcTemplate jdbc;
    private final TaskMapper taskMapper;
    private final ProjectMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final ProjectService projectService;
    private final RiskService riskService;
    private final AuditService auditService;

    @Override
    public Map<String, Object> acceptance(Long taskId) {
        Task task = task(taskId, false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getId());
        result.put("reviewRequired", Boolean.TRUE.equals(task.getReviewRequired()));
        result.put("status", task.getAcceptanceStatus() == null ? "NOT_REQUIRED" : task.getAcceptanceStatus());
        result.put("submittedAt", task.getAcceptanceSubmittedAt());
        result.put("checklist", jdbc.queryForList("SELECT c.id,c.content,c.checked,c.order_index orderIndex,c.checked_by checkedBy,u.nickname checkedByName,c.checked_at checkedAt,c.created_at createdAt FROM pm_acceptance_checklist c LEFT JOIN sys_user u ON u.id=c.checked_by WHERE c.task_id=? ORDER BY c.order_index,c.id", taskId));
        result.put("reviews", jdbc.queryForList("SELECT r.id,r.action,r.comment,r.evidence_attachment_id evidenceAttachmentId,r.reviewer_id reviewerId,u.nickname reviewerName,r.created_at createdAt FROM pm_task_review r LEFT JOIN sys_user u ON u.id=r.reviewer_id WHERE r.task_id=? ORDER BY r.id DESC", taskId));
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> updateAcceptance(Long taskId, AcceptanceActionDTO dto) {
        Task task = task(taskId, true);
        String action = dto.getAction() == null ? "CONFIGURE" : dto.getAction().toUpperCase(Locale.ROOT);
        Access access = access(task.getProjectId());
        String comment = trim(dto.getComment());
        switch (action) {
            case "CONFIGURE" -> {
                if (dto.getReviewRequired() == null) throw new BusinessException("请明确是否启用验收流程");
                boolean required = Boolean.TRUE.equals(dto.getReviewRequired());
                task.setReviewRequired(required);
                task.setAcceptanceStatus(required ? "NOT_READY" : "NOT_REQUIRED");
                task.setAcceptanceSubmittedAt(null);
            }
            case "SUBMIT" -> {
                if (!Boolean.TRUE.equals(task.getReviewRequired())) throw new BusinessException("该任务未启用验收流程");
                if (!Objects.equals(task.getAssigneeId(), access.userId) && !access.manager) throw new BusinessException("只有任务负责人或项目管理员可以提交验收");
                requireChecklistComplete(taskId);
                task.setAcceptanceStatus("PENDING");
                task.setAcceptanceSubmittedAt(LocalDateTime.now());
                if ("TODO".equals(task.getStatus())) task.setStatus("IN_PROGRESS");
            }
            case "START" -> {
                requireReviewer(access);
                requireStatus(task, "PENDING");
                task.setAcceptanceStatus("IN_REVIEW");
            }
            case "PASS" -> {
                requireReviewer(access);
                if (!Set.of("PENDING", "IN_REVIEW").contains(task.getAcceptanceStatus())) throw new BusinessException("当前状态不能通过验收");
                requireChecklistComplete(taskId);
                task.setAcceptanceStatus("PASSED");
                task.setStatus("DONE");
                task.setCompletedAt(LocalDateTime.now());
            }
            case "REJECT" -> {
                requireReviewer(access);
                if (!Set.of("PENDING", "IN_REVIEW").contains(task.getAcceptanceStatus())) throw new BusinessException("当前状态不能驳回");
                if (comment == null) throw new BusinessException("驳回时必须填写原因");
                task.setAcceptanceStatus("REJECTED");
                task.setStatus("IN_PROGRESS");
                task.setCompletedAt(null);
            }
            case "RETURN_FOR_FIX" -> {
                if (!"QA_TESTER".equals(access.identity)) throw new BusinessException("只有测试工程师可以将已完成任务打回修改");
                if (!"DONE".equals(task.getStatus())) throw new BusinessException("只有已完成任务可以因 Bug 打回修改");
                if (comment == null) throw new BusinessException("打回修改时必须填写 Bug 原因");
                task.setAcceptanceStatus("REJECTED");
                task.setAcceptanceSubmittedAt(null);
                task.setStatus("IN_PROGRESS");
                task.setCompletedAt(null);
            }
            case "RESET" -> {
                if (!access.manager) throw new BusinessException("只有项目负责人或项目管理员可以重置验收");
                task.setAcceptanceStatus(Boolean.TRUE.equals(task.getReviewRequired()) ? "NOT_READY" : "NOT_REQUIRED");
                task.setAcceptanceSubmittedAt(null);
                if ("DONE".equals(task.getStatus())) { task.setStatus("IN_PROGRESS"); task.setCompletedAt(null); }
            }
            default -> throw new BusinessException("无效的验收操作");
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        if (dto.getEvidenceAttachmentId() != null) {
            Integer evidenceCount = jdbc.queryForObject("SELECT COUNT(*) FROM pm_task_attachment WHERE id=? AND task_id=? AND project_id=? AND deleted_at IS NULL",
                    Integer.class, dto.getEvidenceAttachmentId(), taskId, task.getProjectId());
            if (evidenceCount == null || evidenceCount == 0) throw new BusinessException("交付证据不存在或不属于当前任务");
        }
        jdbc.update("INSERT INTO pm_task_review(task_id,project_id,reviewer_id,action,comment,evidence_attachment_id) VALUES (?,?,?,?,?,?)",
                taskId, task.getProjectId(), access.userId, action, comment, dto.getEvidenceAttachmentId());
        auditService.record(task.getProjectId(), "ACCEPTANCE", action, "TASK", taskId,
                "任务验收状态变更为 " + task.getAcceptanceStatus(), Map.of("acceptanceStatus", task.getAcceptanceStatus()));
        riskService.invalidate(task.getProjectId());
        return acceptance(taskId);
    }

    @Override
    @Transactional
    public Map<String, Object> addChecklist(Long taskId, AcceptanceChecklistDTO dto) {
        Task task = task(taskId, true);
        if ("PASSED".equals(task.getAcceptanceStatus())) throw new BusinessException("已通过验收的任务不能修改清单");
        int order = dto.getOrderIndex() == null ? Optional.ofNullable(jdbc.queryForObject("SELECT COALESCE(MAX(order_index),-1)+1 FROM pm_acceptance_checklist WHERE task_id=?", Integer.class, taskId)).orElse(0) : dto.getOrderIndex();
        Long id = insertKey("INSERT INTO pm_acceptance_checklist(task_id,project_id,content,order_index,created_by) VALUES (?,?,?,?,?)",
                taskId, task.getProjectId(), dto.getContent().trim(), order, UserHolder.getUserId());
        return jdbc.queryForMap("SELECT id,content,checked,order_index orderIndex,created_at createdAt FROM pm_acceptance_checklist WHERE id=?", id);
    }

    @Override
    @Transactional
    public Map<String, Object> toggleChecklist(Long taskId, Long itemId, boolean checked) {
        Task task = task(taskId, true);
        int changed = jdbc.update("UPDATE pm_acceptance_checklist SET checked=?,checked_by=?,checked_at=? WHERE id=? AND task_id=? AND project_id=?",
                checked, checked ? UserHolder.getUserId() : null, checked ? LocalDateTime.now() : null, itemId, taskId, task.getProjectId());
        if (changed == 0) throw new BusinessException("验收清单项不存在");
        return jdbc.queryForMap("SELECT id,content,checked,order_index orderIndex,checked_by checkedBy,checked_at checkedAt FROM pm_acceptance_checklist WHERE id=?", itemId);
    }

    @Override
    @Transactional
    public void deleteChecklist(Long taskId, Long itemId) {
        Task task = task(taskId, true);
        if ("PASSED".equals(task.getAcceptanceStatus())) throw new BusinessException("已通过验收的任务不能修改清单");
        if (jdbc.update("DELETE FROM pm_acceptance_checklist WHERE id=? AND task_id=? AND project_id=?", itemId, taskId, task.getProjectId()) == 0) throw new BusinessException("验收清单项不存在");
    }

    @Override
    public List<Map<String, Object>> timeEntries(Long taskId) {
        task(taskId, false);
        return jdbc.queryForList("SELECT e.id,e.user_id userId,u.nickname userName,e.work_date workDate,e.hours,e.note,e.created_at createdAt FROM pm_time_entry e LEFT JOIN sys_user u ON u.id=e.user_id WHERE e.task_id=? ORDER BY e.work_date DESC,e.id DESC", taskId);
    }

    @Override
    @Transactional
    public Map<String, Object> addTimeEntry(Long taskId, TimeEntryDTO dto) {
        Task task = task(taskId, true);
        LocalDate workDate = LocalDate.parse(dto.getWorkDate());
        if (workDate.isAfter(LocalDate.now())) throw new BusinessException("不能登记未来日期的工时");
        Long id = insertKey("INSERT INTO pm_time_entry(task_id,project_id,user_id,work_date,hours,note) VALUES (?,?,?,?,?,?)",
                taskId, task.getProjectId(), UserHolder.getUserId(), workDate, dto.getHours(), trim(dto.getNote()));
        BigDecimal sum = jdbc.queryForObject("SELECT COALESCE(SUM(hours),0) FROM pm_time_entry WHERE task_id=?", BigDecimal.class, taskId);
        task.setActualHours(sum == null ? 0 : sum.setScale(0, RoundingMode.HALF_UP).intValue());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        auditService.record(task.getProjectId(), "TIME", "TIME_RECORDED", "TASK", taskId,
                "登记工时 " + dto.getHours() + " 小时", Map.of("workDate", workDate.toString()));
        return jdbc.queryForMap("SELECT id,user_id userId,work_date workDate,hours,note,created_at createdAt FROM pm_time_entry WHERE id=?", id);
    }

    @Override
    public List<Map<String, Object>> capacity(Long projectId) {
        projectService.assertProjectAccess(projectId, false);
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        List<Map<String, Object>> members = jdbc.queryForList("SELECT pm.user_id userId,u.nickname,pm.identity,pm.permission FROM pm_project_member pm JOIN sys_user u ON u.id=pm.user_id WHERE pm.project_id=? ORDER BY pm.joined_at", projectId);
        for (Map<String, Object> member : members) {
            Long userId = ((Number) member.get("userId")).longValue();
            BigDecimal weekly = jdbc.query("SELECT weekly_hours FROM pm_member_capacity WHERE project_id=? AND user_id=? AND effective_from<=? ORDER BY effective_from DESC LIMIT 1",
                    rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.valueOf(40), projectId, userId, LocalDate.now());
            BigDecimal adjustment = jdbc.queryForObject("SELECT COALESCE(SUM(available_hours-8),0) FROM pm_capacity_exception WHERE project_id=? AND user_id=? AND exception_date BETWEEN ? AND ?",
                    BigDecimal.class, projectId, userId, monday, sunday);
            BigDecimal available = weekly.add(adjustment == null ? BigDecimal.ZERO : adjustment).max(BigDecimal.ZERO);
            BigDecimal remaining = jdbc.queryForObject("SELECT COALESCE(SUM(GREATEST(COALESCE(estimated_hours,0)-COALESCE(actual_hours,0),0)),0) FROM sys_task WHERE project_id=? AND assignee_id=? AND status<>'DONE' AND deleted_at IS NULL",
                    BigDecimal.class, projectId, userId);
            BigDecimal utilization = available.signum() == 0 ? (remaining.signum() == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(999)) : remaining.multiply(BigDecimal.valueOf(100)).divide(available, 1, RoundingMode.HALF_UP);
            member.put("weeklyHours", weekly);
            member.put("availableHours", available);
            member.put("remainingHours", remaining);
            member.put("utilizationPercent", utilization);
            member.put("overloaded", utilization.compareTo(BigDecimal.valueOf(100)) > 0);
            member.put("exceptions", jdbc.queryForList("SELECT id,exception_date exceptionDate,available_hours availableHours,reason,created_at createdAt FROM pm_capacity_exception WHERE project_id=? AND user_id=? AND exception_date BETWEEN ? AND ? ORDER BY exception_date",
                    projectId, userId, monday, sunday));
        }
        return members;
    }

    @Override
    @Transactional
    public Map<String, Object> updateCapacity(Long projectId, CapacityUpdateDTO dto) {
        projectService.assertProjectAccess(projectId, true);
        Access access = access(projectId);
        if (!access.manager) throw new BusinessException("只有项目负责人、项目经理或项目管理员可以配置容量");
        assertMember(projectId, dto.getUserId());
        LocalDate effective = dto.getEffectiveFrom() == null ? LocalDate.now() : LocalDate.parse(dto.getEffectiveFrom());
        jdbc.update("INSERT INTO pm_member_capacity(project_id,user_id,weekly_hours,effective_from,updated_by) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE weekly_hours=VALUES(weekly_hours),updated_by=VALUES(updated_by),updated_at=NOW()",
                projectId, dto.getUserId(), dto.getWeeklyHours(), effective, access.userId);
        auditService.record(projectId, "CAPACITY", "CAPACITY_UPDATED", "USER", dto.getUserId(),
                "更新成员周容量为 " + dto.getWeeklyHours() + " 小时", Map.of("effectiveFrom", effective.toString()));
        return capacity(projectId).stream().filter(item -> Objects.equals(((Number) item.get("userId")).longValue(), dto.getUserId())).findFirst().orElseThrow();
    }

    @Override
    @Transactional
    public Map<String, Object> addCapacityException(Long projectId, CapacityExceptionDTO dto) {
        projectService.assertProjectAccess(projectId, true);
        Access access = access(projectId);
        if (!access.manager) throw new BusinessException("只有项目负责人、项目经理或项目管理员可以配置容量例外");
        assertMember(projectId, dto.getUserId());
        LocalDate date = LocalDate.parse(dto.getExceptionDate());
        jdbc.update("INSERT INTO pm_capacity_exception(project_id,user_id,exception_date,available_hours,reason,created_by) VALUES (?,?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE available_hours=VALUES(available_hours),reason=VALUES(reason),created_by=VALUES(created_by),created_at=NOW()",
                projectId, dto.getUserId(), date, dto.getAvailableHours(), trim(dto.getReason()), access.userId);
        Map<String, Object> exception = jdbc.queryForMap("SELECT id,user_id userId,exception_date exceptionDate,available_hours availableHours,reason,created_at createdAt FROM pm_capacity_exception WHERE project_id=? AND user_id=? AND exception_date=?",
                projectId, dto.getUserId(), date);
        auditService.record(projectId, "CAPACITY", "CAPACITY_EXCEPTION_SAVED", "USER", dto.getUserId(),
                "配置 " + date + " 可用工时为 " + dto.getAvailableHours(), Map.of("exceptionDate", date.toString()));
        return exception;
    }

    @Override
    @Transactional
    public void deleteCapacityException(Long projectId, Long exceptionId) {
        projectService.assertProjectAccess(projectId, true);
        Access access = access(projectId);
        if (!access.manager) throw new BusinessException("只有项目负责人、项目经理或项目管理员可以删除容量例外");
        int deleted = jdbc.update("DELETE FROM pm_capacity_exception WHERE id=? AND project_id=?", exceptionId, projectId);
        if (deleted == 0) throw new BusinessException("容量例外不存在");
        auditService.record(projectId, "CAPACITY", "CAPACITY_EXCEPTION_DELETED", "CAPACITY_EXCEPTION", exceptionId,
                "删除容量例外", Map.of());
    }

    private Task task(Long taskId, boolean write) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || task.getDeletedAt() != null) throw new BusinessException("任务不存在或已移入回收站");
        projectService.assertProjectAccess(task.getProjectId(), write);
        return task;
    }

    private Access access(Long projectId) {
        Long userId = UserHolder.getUserId();
        Project project = projectService.getByIdForAccess(projectId);
        ProjectMember member = memberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, userId));
        User user = userMapper.selectById(userId);
        String identity = member != null && member.getIdentity() != null ? member.getIdentity() : user == null ? null : user.getIdentity();
        boolean admin = Objects.equals(project.getCreatorId(), userId) || member != null && "PROJECT_ADMIN".equals(member.getPermission());
        return new Access(userId, identity, admin || "PROJECT_MANAGER".equals(identity), admin || REVIEWER_IDENTITIES.contains(identity));
    }

    private void requireReviewer(Access access) { if (!access.reviewer) throw new BusinessException("当前岗位无权执行验收评审"); }
    private void requireStatus(Task task, String status) { if (!status.equals(task.getAcceptanceStatus())) throw new BusinessException("当前验收状态不能执行此操作"); }
    private void requireChecklistComplete(Long taskId) {
        Integer unchecked = jdbc.queryForObject("SELECT COUNT(*) FROM pm_acceptance_checklist WHERE task_id=? AND checked=0", Integer.class, taskId);
        if (unchecked != null && unchecked > 0) throw new BusinessException("请先完成全部验收清单项");
    }
    private void assertMember(Long projectId, Long userId) {
        Project project = projectService.getByIdForAccess(projectId);
        if (Objects.equals(project.getCreatorId(), userId)) return;
        if (memberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, userId)) == 0) throw new BusinessException("用户不是当前项目成员");
    }
    private Long insertKey(String sql, Object... args) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> { PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS); for (int i=0;i<args.length;i++) ps.setObject(i+1,args[i]); return ps; }, holder);
        if (holder.getKey() == null) throw new IllegalStateException("未能读取新增记录 ID");
        return holder.getKey().longValue();
    }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private record Access(Long userId, String identity, boolean manager, boolean reviewer) { }
}
