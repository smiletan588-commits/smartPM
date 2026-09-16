package com.smartpm.service.impl;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.entity.Project;
import com.smartpm.service.*;
import com.smartpm.vo.RiskOverviewVO;
import com.smartpm.vo.ScheduleAnalysisVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ManagementOperationsServiceImpl implements ManagementOperationsService {
    private final JdbcTemplate jdbc;
    private final ProjectService projectService;
    private final ScheduleService scheduleService;
    private final RiskService riskService;
    private final DeliveryCollaborationService deliveryService;
    private final UserService userService;
    private final AuditService auditService;

    @Override
    public Map<String, Object> executiveSummary(Long projectId) {
        projectService.assertProjectAccess(projectId, false);
        Project project = projectService.getByIdForAccess(projectId);
        ScheduleAnalysisVO schedule;
        try { schedule = scheduleService.analyze(projectId, null); }
        catch (RuntimeException e) { schedule = new ScheduleAnalysisVO(); schedule.getWarnings().add(e.getMessage()); }
        RiskOverviewVO risks = riskService.getOverview(projectId, null);
        List<Map<String, Object>> capacities = deliveryService.capacity(projectId);
        List<Map<String, Object>> milestones = jdbc.queryForList("SELECT id,name,target_date targetDate,status FROM pm_project_milestone WHERE project_id=? ORDER BY target_date", projectId);
        int overdueMilestones = (int) milestones.stream()
                .filter(item -> !"COMPLETED".equals(item.get("status")))
                .map(item -> item.get("targetDate")).filter(Objects::nonNull)
                .map(Object::toString).map(LocalDate::parse).filter(date -> date.isBefore(LocalDate.now())).count();
        int overloaded = (int) capacities.stream().filter(item -> Boolean.TRUE.equals(item.get("overloaded"))).count();
        int unassigned = value("SELECT COUNT(*) FROM sys_task WHERE project_id=? AND parent_id IS NULL AND status<>'DONE' AND assignee_id IS NULL AND deleted_at IS NULL", projectId);
        int blocked = value("SELECT COUNT(DISTINCT d.task_id) FROM pm_task_dependency d JOIN sys_task t ON t.id=d.task_id JOIN sys_task p ON p.id=d.prerequisite_task_id WHERE t.project_id=? AND t.status<>'DONE' AND p.status<>'DONE' AND t.deleted_at IS NULL", projectId);

        List<String> reasons = new ArrayList<>();
        String health = "GREEN";
        if (risks.getHighCount() > 0) { health = "RED"; reasons.add("存在 " + risks.getHighCount() + " 个高风险任务"); }
        if (schedule.getFinishVarianceDays() != null && schedule.getFinishVarianceDays() >= 4) { health = "RED"; reasons.add("预计完工相对基线延期 " + schedule.getFinishVarianceDays() + " 天"); }
        if (overdueMilestones > 0) { health = "RED"; reasons.add("存在 " + overdueMilestones + " 个延期里程碑"); }
        if (!"RED".equals(health) && risks.getMediumCount() > 0) { health = "AMBER"; reasons.add("存在 " + risks.getMediumCount() + " 个中风险任务"); }
        if (!"RED".equals(health) && schedule.getFinishVarianceDays() != null && schedule.getFinishVarianceDays() > 0) { health = "AMBER"; reasons.add("预计完工相对基线延期 " + schedule.getFinishVarianceDays() + " 天"); }
        if (!"RED".equals(health) && overloaded > 0) { health = "AMBER"; reasons.add(overloaded + " 名成员负载超过 100%"); }
        if (reasons.isEmpty()) reasons.add("当前未发现高优先级交付风险");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId); result.put("projectName", project.getName()); result.put("generatedAt", LocalDateTime.now());
        result.put("health", Map.of("level", health, "reasons", reasons));
        Map<String, Object> scheduleMap = new LinkedHashMap<>();
        scheduleMap.put("startDate", schedule.getProjectStartDate()); scheduleMap.put("finishDate", schedule.getProjectFinishDate());
        scheduleMap.put("baselineName", schedule.getBaselineName()); scheduleMap.put("finishVarianceDays", schedule.getFinishVarianceDays()); scheduleMap.put("criticalTaskCount", schedule.getCriticalTaskCount());
        result.put("schedule", scheduleMap);
        result.put("risks", Map.of("high", risks.getHighCount(), "medium", risks.getMediumCount(), "low", risks.getLowCount()));
        result.put("milestones", milestones); result.put("overdueMilestoneCount", overdueMilestones);
        result.put("capacity", capacities); result.put("overloadedMemberCount", overloaded);
        result.put("unassignedTaskCount", unassigned); result.put("blockedTaskCount", blocked);
        result.put("openDecisions", jdbc.queryForList("SELECT id,title,due_date dueDate,owner_id ownerId FROM pm_product_decision WHERE project_id=? AND status='OPEN' ORDER BY due_date,id LIMIT 20", projectId));
        result.put("recentActivities", jdbc.queryForList("SELECT a.id,a.action_type actionType,a.summary,u.nickname actorName,a.created_at createdAt FROM pm_task_activity a LEFT JOIN sys_user u ON u.id=a.actor_id WHERE a.project_id=? ORDER BY a.created_at DESC LIMIT 12", projectId));
        return result;
    }

    @Override
    public byte[] executiveSummaryPdf(Long projectId) {
        Map<String, Object> summary = executiveSummary(projectId);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 42, 42, 44, 44);
            PdfWriter.getInstance(document, output);
            document.open();
            Font title = font(20, Font.BOLD, new java.awt.Color(24, 39, 75));
            Font heading = font(12, Font.BOLD, new java.awt.Color(46, 91, 238));
            Font body = font(10, Font.NORMAL, new java.awt.Color(55, 65, 81));
            document.add(new Paragraph(summary.get("projectName") + " · 项目简报", title));
            document.add(new Paragraph("生成时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), body));
            document.add(Chunk.NEWLINE);
            Map<?, ?> health = (Map<?, ?>) summary.get("health");
            document.add(new Paragraph("项目健康度：" + health.get("level"), heading));
            for (Object reason : (List<?>) health.get("reasons")) document.add(new Paragraph("• " + reason, body));
            document.add(Chunk.NEWLINE);
            Map<?, ?> schedule = (Map<?, ?>) summary.get("schedule");
            PdfPTable metrics = new PdfPTable(2); metrics.setWidthPercentage(100);
            addMetric(metrics, "预计完工", Objects.toString(schedule.get("finishDate"), "暂无"), body);
            addMetric(metrics, "关键任务", Objects.toString(schedule.get("criticalTaskCount"), "0"), body);
            addMetric(metrics, "高风险", String.valueOf(((Map<?, ?>) summary.get("risks")).get("high")), body);
            addMetric(metrics, "延期里程碑", String.valueOf(summary.get("overdueMilestoneCount")), body);
            addMetric(metrics, "未分配任务", String.valueOf(summary.get("unassignedTaskCount")), body);
            addMetric(metrics, "阻塞任务", String.valueOf(summary.get("blockedTaskCount")), body);
            document.add(metrics);
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("里程碑", heading));
            for (Map<?, ?> item : (List<Map<?, ?>>) summary.get("milestones")) document.add(new Paragraph("• " + item.get("name") + "  " + Objects.toString(item.get("targetDate"), "未定") + "  " + item.get("status"), body));
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("待确认决策", heading));
            List<Map<?, ?>> decisions = (List<Map<?, ?>>) summary.get("openDecisions");
            if (decisions.isEmpty()) document.add(new Paragraph("暂无待确认决策", body));
            else for (Map<?, ?> item : decisions) document.add(new Paragraph("• " + item.get("title"), body));
            document.close();
            auditService.record(projectId, "EXECUTIVE_SUMMARY", "PDF_GENERATED", "PROJECT", projectId, "生成项目简报 PDF", Map.of());
            return output.toByteArray();
        } catch (Exception e) { throw new BusinessException("项目简报 PDF 生成失败：" + e.getMessage()); }
    }

    @Override
    public int emailExecutiveSummary(Long projectId, List<Long> recipientUserIds) {
        projectService.assertProjectAccess(projectId, true);
        if (!projectService.canManageMembers(projectId)) throw new BusinessException("只有项目负责人或项目管理员可以发送项目简报");
        Map<String, Object> summary = executiveSummary(projectId);
        String body = plainText(summary);
        int enqueued = 0;
        for (Long userId : recipientUserIds.stream().filter(Objects::nonNull).distinct().toList()) {
            Integer member = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_member WHERE project_id=? AND user_id=?", Integer.class, projectId, userId);
            Project project = projectService.getByIdForAccess(projectId);
            if ((member == null || member == 0) && !Objects.equals(project.getCreatorId(), userId)) throw new BusinessException("收件人不是项目成员");
            List<Map<String, Object>> users = jdbc.queryForList("SELECT email,email_verified_at verifiedAt FROM sys_user WHERE id=?", userId);
            if (users.isEmpty() || users.get(0).get("email") == null || users.get(0).get("verifiedAt") == null) continue;
            String key = "summary:" + projectId + ":" + userId + ":" + LocalDate.now();
            try {
                jdbc.update("INSERT INTO pm_email_outbox(user_id,recipient,subject,body,status,attempts,next_attempt_at,dedupe_key) VALUES (?,?,?,?,'PENDING',0,NOW(),?)",
                        userId, users.get(0).get("email"), summary.get("projectName") + " · 项目简报", body, key);
                enqueued++;
            } catch (DuplicateKeyException ignored) { }
        }
        auditService.record(projectId, "EXECUTIVE_SUMMARY", "EMAIL_QUEUED", "PROJECT", projectId, "项目简报已加入邮件队列", Map.of("recipientCount", enqueued));
        return enqueued;
    }

    @Override
    public List<Map<String, Object>> auditEvents(Long projectId, String category, int page, int size) {
        requireAdmin();
        int safeSize = Math.max(1, Math.min(100, size)); int offset = Math.max(0, page - 1) * safeSize;
        String sql = "SELECT a.id,a.actor_id actorId,u.nickname actorName,a.project_id projectId,a.category,a.action,a.target_type targetType,a.target_id targetId,a.summary,a.metadata,a.created_at createdAt FROM pm_audit_event a LEFT JOIN sys_user u ON u.id=a.actor_id WHERE (? IS NULL OR a.project_id=?) AND (? IS NULL OR a.category=?) ORDER BY a.id DESC LIMIT ? OFFSET ?";
        return jdbc.queryForList(sql, projectId, projectId, blank(category), blank(category), safeSize, offset);
    }

    @Override
    public List<Map<String, Object>> loginEvents(Boolean success, int page, int size) {
        requireAdmin();
        int safeSize = Math.max(1, Math.min(100, size));
        int offset = Math.max(0, page - 1) * safeSize;
        return jdbc.queryForList("SELECT e.id,e.user_id userId,e.username,e.success,e.reason,e.created_at createdAt " +
                        "FROM pm_login_event e WHERE (? IS NULL OR e.success=?) ORDER BY e.id DESC LIMIT ? OFFSET ?",
                success, success, safeSize, offset);
    }

    @Override
    public Map<String, Object> systemOverview() {
        requireAdmin();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("users", value("SELECT COUNT(*) FROM sys_user")); result.put("activeUsers", value("SELECT COUNT(*) FROM sys_user WHERE status='ACTIVE'"));
        result.put("projects", value("SELECT COUNT(*) FROM sys_project WHERE deleted_at IS NULL")); result.put("tasks", value("SELECT COUNT(*) FROM sys_task WHERE deleted_at IS NULL"));
        result.put("pendingEmails", value("SELECT COUNT(*) FROM pm_email_outbox WHERE status='PENDING'")); result.put("failedEmails", value("SELECT COUNT(*) FROM pm_email_outbox WHERE status='FAILED'"));
        result.put("loginFailures24h", value("SELECT COUNT(*) FROM pm_login_event WHERE success=0 AND created_at>=DATE_SUB(NOW(),INTERVAL 24 HOUR)"));
        result.put("openOperations", jdbc.queryForList("SELECT id,event_type eventType,severity,source,message,created_at createdAt FROM pm_system_operation_event WHERE resolved_at IS NULL ORDER BY FIELD(severity,'CRITICAL','ERROR','WARN','INFO'),created_at DESC LIMIT 20"));
        result.put("migrationVersion", jdbc.query("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1", rs -> rs.next() ? rs.getString(1) : null));
        result.put("generatedAt", LocalDateTime.now());
        return result;
    }

    private String plainText(Map<String, Object> summary) {
        Map<?, ?> health = (Map<?, ?>) summary.get("health"); Map<?, ?> schedule = (Map<?, ?>) summary.get("schedule"); Map<?, ?> risks = (Map<?, ?>) summary.get("risks");
        return summary.get("projectName") + " 项目简报\n\n健康度：" + health.get("level") + "\n原因：" + String.join("；", ((List<?>) health.get("reasons")).stream().map(String::valueOf).toList()) +
                "\n预计完工：" + Objects.toString(schedule.get("finishDate"), "暂无") + "\n基线偏差：" + Objects.toString(schedule.get("finishVarianceDays"), "暂无") +
                "\n风险：高 " + risks.get("high") + " / 中 " + risks.get("medium") + " / 低 " + risks.get("low") +
                "\n延期里程碑：" + summary.get("overdueMilestoneCount") + "\n未分配任务：" + summary.get("unassignedTaskCount") + "\n阻塞任务：" + summary.get("blockedTaskCount");
    }

    private Font font(float size, int style, java.awt.Color color) throws Exception {
        String[] candidates = {"C:/Windows/Fonts/msyh.ttc,0", "/usr/share/fonts/noto/NotoSansCJK-Regular.ttc,0", "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0"};
        for (String candidate : candidates) {
            String file = candidate.substring(0, candidate.lastIndexOf(','));
            if (new File(file).exists()) return new Font(BaseFont.createFont(candidate, BaseFont.IDENTITY_H, BaseFont.EMBEDDED), size, style, color);
        }
        return new Font(Font.HELVETICA, size, style, color);
    }
    private void addMetric(PdfPTable table, String label, String value, Font font) { table.addCell(new Phrase(label + "\n" + value, font)); }
    private void requireAdmin() { if (!userService.isSystemAdmin(UserHolder.getUserId())) throw new BusinessException("仅系统管理员可以查看系统运营信息"); }
    private int value(String sql, Object... args) { Integer count = jdbc.queryForObject(sql, Integer.class, args); return count == null ? 0 : count; }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT); }
}
