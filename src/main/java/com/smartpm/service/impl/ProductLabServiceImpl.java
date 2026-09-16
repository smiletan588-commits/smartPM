package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.dto.ProductArtifactApplyDTO;
import com.smartpm.dto.ProductArtifactDTO;
import com.smartpm.dto.ProductConversationCreateDTO;
import com.smartpm.dto.ProductMessageDTO;
import com.smartpm.entity.Project;
import com.smartpm.entity.ProjectMember;
import com.smartpm.entity.User;
import com.smartpm.entity.Wiki;
import com.smartpm.mapper.ProjectMemberMapper;
import com.smartpm.mapper.UserMapper;
import com.smartpm.service.AIService;
import com.smartpm.service.AuditService;
import com.smartpm.service.ProductLabService;
import com.smartpm.service.ProjectService;
import com.smartpm.service.TaskService;
import com.smartpm.service.WikiService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductLabServiceImpl implements ProductLabService {
    private static final Set<String> CURATOR_IDENTITIES = Set.of("PRODUCT_MANAGER", "PROJECT_MANAGER");
    private static final Set<String> APPLY_TARGETS = Set.of("WIKI", "TASKS", "CHECKLIST", "MILESTONES", "DECISIONS");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ProjectService projectService;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserMapper userMapper;
    private final AIService aiService;
    private final WikiService wikiService;
    private final TaskService taskService;
    private final AuditService auditService;

    @Override
    public List<Map<String, Object>> conversations(Long projectId) {
        Access access = access(projectId, false);
        String visibility = access.viewer ? " AND c.created_by=?" : "";
        List<Object> args = new ArrayList<>(List.of(projectId));
        if (access.viewer) args.add(access.userId);
        return jdbc.queryForList("SELECT c.id, c.project_id projectId, c.title, c.stage, c.mode, c.status, " +
                "c.created_by createdBy, u.nickname creatorName, c.created_at createdAt, c.updated_at updatedAt, " +
                "(SELECT COUNT(*) FROM pm_product_message m WHERE m.conversation_id=c.id) messageCount " +
                "FROM pm_product_conversation c LEFT JOIN sys_user u ON u.id=c.created_by " +
                "WHERE c.project_id=?" + visibility + " ORDER BY c.updated_at DESC", args.toArray());
    }

    @Override
    @Transactional
    public Map<String, Object> createConversation(Long projectId, ProductConversationCreateDTO dto) {
        Access access = access(projectId, true);
        Long id = insertAndReturnKey("INSERT INTO pm_product_conversation(project_id,title,stage,mode,created_by) VALUES (?,?,?,?,?)",
                projectId, dto.getTitle().trim(), defaultValue(dto.getStage(), "IDEA"),
                defaultValue(dto.getMode(), "IDEA_REFINEMENT"), access.userId);
        auditService.record(projectId, "PRODUCT_LAB", "CONVERSATION_CREATED", "PRODUCT_CONVERSATION", id,
                "创建产品共创会话：" + dto.getTitle().trim(), Map.of());
        return conversationSummary(id, projectId);
    }

    @Override
    public Map<String, Object> conversation(Long projectId, Long conversationId) {
        Access access = access(projectId, false);
        Map<String, Object> conversation = requireConversation(projectId, conversationId);
        if (access.viewer && !Objects.equals(number(conversation.get("createdBy")), access.userId)) {
            throw new BusinessException("只读成员只能查看已发布成果");
        }
        conversation.put("messages", jdbc.queryForList("SELECT m.id, m.role, m.content, m.status, m.context_json contextJson, " +
                "m.sender_id senderId, u.nickname senderName, m.created_at createdAt FROM pm_product_message m " +
                "LEFT JOIN sys_user u ON u.id=m.sender_id WHERE m.conversation_id=? ORDER BY m.id", conversationId));
        conversation.put("artifacts", artifacts(projectId, conversationId));
        return conversation;
    }

    @Override
    public Flux<String> streamMessage(Long projectId, Long conversationId, ProductMessageDTO dto) {
        Access access = access(projectId, true);
        Map<String, Object> conversation = requireConversation(projectId, conversationId);
        if (!access.curator && !Objects.equals(number(conversation.get("createdBy")), access.userId)) {
            throw new BusinessException("只能继续自己的产品共创会话");
        }
        Map<String, Object> selectedContext = selectedContext(projectId, dto);
        String contextJson = json(selectedContext);
        insertMessage(conversationId, projectId, access.userId, "USER", dto.getMessage().trim(), "COMPLETE", contextJson);
        jdbc.update("UPDATE pm_product_conversation SET mode=?, updated_at=NOW() WHERE id=?",
                defaultValue(dto.getMode(), String.valueOf(conversation.get("mode"))), conversationId);

        String prompt = buildPrompt(conversation, dto, selectedContext);
        StringBuilder answer = new StringBuilder();
        return aiService.streamChat(prompt)
                .doOnNext(answer::append)
                .doOnComplete(() -> saveAssistant(conversationId, projectId, access.userId, answer.toString(), "COMPLETE"))
                .doOnError(error -> saveAssistant(conversationId, projectId, access.userId, answer.toString(),
                        answer.isEmpty() ? "FAILED" : "INTERRUPTED"))
                .doOnCancel(() -> saveAssistant(conversationId, projectId, access.userId, answer.toString(), "INTERRUPTED"));
    }

    @Override
    public List<Map<String, Object>> artifacts(Long projectId, Long conversationId) {
        Access access = access(projectId, false);
        StringBuilder sql = new StringBuilder("SELECT a.id, a.project_id projectId, a.conversation_id conversationId, a.type, a.title, " +
                "a.content, a.status, a.version_no versionNo, a.created_by createdBy, a.updated_by updatedBy, " +
                "a.published_wiki_id publishedWikiId, a.applied_at appliedAt, a.created_at createdAt, a.updated_at updatedAt " +
                "FROM pm_product_artifact a WHERE a.project_id=?");
        List<Object> args = new ArrayList<>(List.of(projectId));
        if (conversationId != null) { sql.append(" AND a.conversation_id=?"); args.add(conversationId); }
        if (access.viewer) sql.append(" AND a.status='PUBLISHED'");
        sql.append(" ORDER BY a.updated_at DESC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Override
    public Map<String, Object> artifact(Long projectId, Long artifactId) {
        Access access = access(projectId, false);
        Map<String, Object> artifact = requireArtifact(projectId, artifactId);
        if (access.viewer && !"PUBLISHED".equals(artifact.get("status"))) {
            throw new BusinessException("只读成员只能查看已发布成果");
        }
        artifact.put("versions", jdbc.queryForList("SELECT v.id,v.version_no versionNo,v.title,v.content,v.status," +
                "v.edited_by editedBy,u.nickname editorName,v.created_at createdAt FROM pm_product_artifact_version v " +
                "LEFT JOIN sys_user u ON u.id=v.edited_by WHERE v.project_id=? AND v.artifact_id=? ORDER BY v.version_no DESC",
                projectId, artifactId));
        return artifact;
    }

    @Override
    @Transactional
    public Map<String, Object> createArtifact(Long projectId, ProductArtifactDTO dto) {
        Access access = access(projectId, true);
        if (dto.getConversationId() != null) requireConversation(projectId, dto.getConversationId());
        String status = defaultValue(dto.getStatus(), "DRAFT");
        Long id = insertAndReturnKey("INSERT INTO pm_product_artifact(project_id,conversation_id,type,title,content,status,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?)",
                projectId, dto.getConversationId(), dto.getType(), dto.getTitle().trim(), dto.getContent(), status, access.userId, access.userId);
        insertVersion(id, projectId, 1, dto.getTitle().trim(), dto.getContent(), status, access.userId);
        auditService.record(projectId, "PRODUCT_LAB", "ARTIFACT_CREATED", "PRODUCT_ARTIFACT", id,
                "创建产品成果：" + dto.getTitle().trim(), Map.of("type", dto.getType()));
        return requireArtifact(projectId, id);
    }

    @Override
    @Transactional
    public Map<String, Object> updateArtifact(Long projectId, Long artifactId, ProductArtifactDTO dto) {
        Access access = access(projectId, true);
        Map<String, Object> artifact = requireArtifact(projectId, artifactId);
        requireArtifactEditor(access, artifact);
        if ("PUBLISHED".equals(artifact.get("status"))) throw new BusinessException("已发布成果不可直接修改，请创建新的草稿成果");
        int version = ((Number) artifact.get("versionNo")).intValue() + 1;
        String status = defaultValue(dto.getStatus(), String.valueOf(artifact.get("status")));
        jdbc.update("UPDATE pm_product_artifact SET type=?, title=?, content=?, status=?, version_no=?, updated_by=?, updated_at=NOW() WHERE id=?",
                dto.getType(), dto.getTitle().trim(), dto.getContent(), status, version, access.userId, artifactId);
        insertVersion(artifactId, projectId, version, dto.getTitle().trim(), dto.getContent(), status, access.userId);
        auditService.record(projectId, "PRODUCT_LAB", "ARTIFACT_UPDATED", "PRODUCT_ARTIFACT", artifactId,
                "更新产品成果：" + dto.getTitle().trim(), Map.of("version", version));
        return requireArtifact(projectId, artifactId);
    }

    @Override
    @Transactional
    public Map<String, Object> publishArtifact(Long projectId, Long artifactId) {
        Access access = access(projectId, true);
        requireCurator(access);
        Map<String, Object> artifact = requireArtifact(projectId, artifactId);
        Wiki wiki = wikiService.create(projectId, String.valueOf(artifact.get("title")), String.valueOf(artifact.get("content")));
        jdbc.update("UPDATE pm_product_artifact SET status='PUBLISHED', published_wiki_id=?, updated_by=?, updated_at=NOW() WHERE id=?",
                wiki.getId(), access.userId, artifactId);
        auditService.record(projectId, "PRODUCT_LAB", "ARTIFACT_PUBLISHED", "PRODUCT_ARTIFACT", artifactId,
                "产品成果已发布到 Wiki", Map.of("wikiId", wiki.getId()));
        return requireArtifact(projectId, artifactId);
    }

    @Override
    public Map<String, Object> previewApply(Long projectId, Long artifactId, ProductArtifactApplyDTO dto) {
        Access access = access(projectId, true);
        requireCurator(access);
        requireArtifact(projectId, artifactId);
        String target = normalizeTarget(dto.getTarget());
        validateApply(projectId, target, dto);
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("target", target);
        preview.put("willCreate", switch (target) {
            case "TASKS" -> dto.getTasks().size();
            case "CHECKLIST" -> dto.getChecklist().size();
            case "MILESTONES" -> dto.getMilestones().size();
            case "DECISIONS" -> dto.getDecisions().size();
            default -> 1;
        });
        preview.put("tasks", dto.getTasks());
        preview.put("checklist", dto.getChecklist());
        preview.put("milestones", dto.getMilestones());
        preview.put("decisions", dto.getDecisions());
        preview.put("requiresConfirmation", true);
        return preview;
    }

    @Override
    @Transactional
    public Map<String, Object> applyArtifact(Long projectId, Long artifactId, ProductArtifactApplyDTO dto) {
        Access access = access(projectId, true);
        requireCurator(access);
        Map<String, Object> artifact = requireArtifact(projectId, artifactId);
        if (!Boolean.TRUE.equals(dto.getConfirmed())) throw new BusinessException("请先预览变更并确认应用");
        String target = normalizeTarget(dto.getTarget());
        validateApply(projectId, target, dto);
        List<Long> createdIds = new ArrayList<>();
        LocalDate today = LocalDate.now();
        switch (target) {
            case "WIKI" -> createdIds.add(wikiService.create(projectId, String.valueOf(artifact.get("title")), String.valueOf(artifact.get("content"))).getId());
            case "TASKS" -> dto.getTasks().forEach(item -> {
                int days = item.getDurationDays() == null ? 15 : item.getDurationDays();
                createdIds.add(taskService.create(projectId, item.getTitle(), item.getDescription(), null,
                        today.plusDays(days - 1L).toString(), today.toString(),
                        defaultValue(item.getPriority(), "MEDIUM"), null, null, null, null, item.getAcceptanceCriteria()).getId());
            });
            case "CHECKLIST" -> dto.getChecklist().forEach(item -> {
                Long id = insertAndReturnKey("INSERT INTO pm_acceptance_checklist(task_id,project_id,content,order_index,created_by) VALUES (?,?,?,?,?)",
                        dto.getTargetTaskId(), projectId, item.getContent(), createdIds.size(), access.userId);
                createdIds.add(id);
            });
            case "MILESTONES" -> dto.getMilestones().forEach(item -> {
                Long id = insertAndReturnKey("INSERT INTO pm_project_milestone(project_id,name,description,target_date,status,created_at,updated_at) VALUES (?,?,?,?, 'PLANNED', NOW(), NOW())",
                        projectId, item.getName(), item.getDescription(), today.plusDays(item.getOffsetDays() == null ? 30 : item.getOffsetDays()));
                createdIds.add(id);
            });
            case "DECISIONS" -> dto.getDecisions().forEach(item -> {
                Long id = insertAndReturnKey("INSERT INTO pm_product_decision(project_id,artifact_id,title,decision,rationale,status,created_by) VALUES (?,?,?,?,?,'OPEN',?)",
                        projectId, artifactId, item.getTitle(), item.getDecision(), item.getRationale(), access.userId);
                createdIds.add(id);
            });
            default -> throw new BusinessException("不支持的应用目标");
        }
        jdbc.update("UPDATE pm_product_artifact SET applied_at=NOW(), updated_by=?, updated_at=NOW() WHERE id=?", access.userId, artifactId);
        auditService.record(projectId, "PRODUCT_LAB", "ARTIFACT_APPLIED", "PRODUCT_ARTIFACT", artifactId,
                "确认应用产品成果", Map.of("target", target, "createdIds", createdIds));
        return Map.of("artifactId", artifactId, "target", target, "createdIds", createdIds, "applied", true);
    }

    private Access access(Long projectId, boolean write) {
        projectService.assertProjectAccess(projectId, write);
        Long userId = UserHolder.getUserId();
        Project project = projectService.getByIdForAccess(projectId);
        ProjectMember member = projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, userId));
        User user = userMapper.selectById(userId);
        String identity = member != null && member.getIdentity() != null ? member.getIdentity() : user == null ? null : user.getIdentity();
        String permission = Objects.equals(project.getCreatorId(), userId) ? "PROJECT_ADMIN" : member == null ? null : member.getPermission();
        boolean viewer = "VIEWER".equals(permission);
        boolean curator = "PROJECT_ADMIN".equals(permission) || CURATOR_IDENTITIES.contains(identity);
        return new Access(userId, identity, permission, viewer, curator);
    }

    private Map<String, Object> selectedContext(Long projectId, ProductMessageDTO dto) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (Boolean.TRUE.equals(dto.getIncludeProjectDescription())) {
            Project project = projectService.getByIdForAccess(projectId);
            context.put("project", Map.of("name", project.getName(), "description", Objects.toString(project.getDescription(), "")));
        }
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (Long id : safe(dto.getTaskIds())) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,title,description,status,priority,start_date startDate,due_date dueDate,acceptance_criteria acceptanceCriteria FROM sys_task WHERE id=? AND project_id=? AND deleted_at IS NULL", id, projectId);
            if (rows.isEmpty()) throw new BusinessException("所选任务不存在或不属于当前项目");
            tasks.add(rows.get(0));
        }
        if (!tasks.isEmpty()) context.put("tasks", tasks);
        List<Map<String, Object>> wikis = new ArrayList<>();
        for (Long id : safe(dto.getWikiIds())) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,title,LEFT(content,8000) content FROM pm_wiki WHERE id=? AND project_id=? AND deleted_at IS NULL", id, projectId);
            if (rows.isEmpty()) throw new BusinessException("所选文档不存在或不属于当前项目");
            wikis.add(rows.get(0));
        }
        if (!wikis.isEmpty()) context.put("wikis", wikis);
        if (Boolean.TRUE.equals(dto.getIncludeRisk())) {
            context.put("riskSummary", jdbc.queryForList("SELECT level riskLevel,COUNT(*) count FROM pm_risk_event WHERE project_id=? AND id IN (SELECT MAX(id) FROM pm_risk_event WHERE project_id=? GROUP BY task_id) GROUP BY level", projectId, projectId));
        }
        if (Boolean.TRUE.equals(dto.getIncludeSchedule())) {
            context.put("schedule", jdbc.queryForList("SELECT b.name,b.project_finish_date finishDate,(SELECT COUNT(*) FROM pm_schedule_baseline_item i WHERE i.baseline_id=b.id AND i.critical=1) criticalTaskCount FROM pm_schedule_baseline b WHERE b.project_id=? ORDER BY b.created_at DESC LIMIT 1", projectId));
        }
        return context;
    }

    private String buildPrompt(Map<String, Object> conversation, ProductMessageDTO dto, Map<String, Object> context) {
        String mode = defaultValue(dto.getMode(), String.valueOf(conversation.get("mode")));
        return "你是 SmartPM 的产品共创顾问。当前工作模式：" + mode + "。\n" +
                "请通过澄清、反向质疑和结构化建议帮助产品经理完善想法。必须把结论分成【已知事实】【用户输入】【推断】【待确认假设】；" +
                "不得声称进行过互联网调研；不得声称已修改任务、文档、风险或排期。需要产出时，用 Markdown 给出可编辑草稿。\n\n" +
                "用户主动选择的项目上下文：\n" + json(context) + "\n\n用户消息：\n" + dto.getMessage().trim();
    }

    private void validateApply(Long projectId, String target, ProductArtifactApplyDTO dto) {
        if ("TASKS".equals(target) && dto.getTasks().isEmpty()) throw new BusinessException("至少需要一个任务草稿");
        if ("CHECKLIST".equals(target)) {
            if (dto.getTargetTaskId() == null || dto.getChecklist().isEmpty()) throw new BusinessException("请选择目标任务并提供验收清单");
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_task WHERE id=? AND project_id=? AND deleted_at IS NULL", Integer.class, dto.getTargetTaskId(), projectId);
            if (count == null || count == 0) throw new BusinessException("目标任务不存在或不属于当前项目");
        }
        if ("MILESTONES".equals(target) && dto.getMilestones().isEmpty()) throw new BusinessException("至少需要一个里程碑草稿");
        if ("DECISIONS".equals(target) && dto.getDecisions().isEmpty()) throw new BusinessException("至少需要一个决策草稿");
    }

    private void requireCurator(Access access) {
        if (!access.curator) throw new BusinessException("只有产品经理、项目经理或项目管理员可以发布和应用成果");
    }

    private void requireArtifactEditor(Access access, Map<String, Object> artifact) {
        if (!access.curator && !Objects.equals(number(artifact.get("createdBy")), access.userId)) {
            throw new BusinessException("只能编辑自己的草稿成果");
        }
    }

    private Map<String, Object> requireConversation(Long projectId, Long conversationId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,project_id projectId,title,stage,mode,status,created_by createdBy,created_at createdAt,updated_at updatedAt FROM pm_product_conversation WHERE id=? AND project_id=?", conversationId, projectId);
        if (rows.isEmpty()) throw new BusinessException("产品共创会话不存在");
        return new LinkedHashMap<>(rows.get(0));
    }

    private Map<String, Object> conversationSummary(Long id, Long projectId) { return requireConversation(projectId, id); }

    private Map<String, Object> requireArtifact(Long projectId, Long artifactId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,project_id projectId,conversation_id conversationId,type,title,content,status,version_no versionNo,created_by createdBy,updated_by updatedBy,published_wiki_id publishedWikiId,applied_at appliedAt,created_at createdAt,updated_at updatedAt FROM pm_product_artifact WHERE id=? AND project_id=?", artifactId, projectId);
        if (rows.isEmpty()) throw new BusinessException("产品成果不存在");
        return new LinkedHashMap<>(rows.get(0));
    }

    private void saveAssistant(Long conversationId, Long projectId, Long userId, String content, String status) {
        if (content == null || content.isBlank()) content = status.equals("FAILED") ? "生成失败，请重试。" : "回答已中断。";
        insertMessage(conversationId, projectId, userId, "ASSISTANT", content, status, "{}");
        jdbc.update("UPDATE pm_product_conversation SET updated_at=NOW() WHERE id=?", conversationId);
    }

    private void insertMessage(Long conversationId, Long projectId, Long senderId, String role, String content, String status, String contextJson) {
        jdbc.update("INSERT INTO pm_product_message(conversation_id,project_id,sender_id,role,content,status,context_json) VALUES (?,?,?,?,?,?,CAST(? AS JSON))",
                conversationId, projectId, senderId, role, content, status, contextJson);
    }

    private void insertVersion(Long artifactId, Long projectId, int version, String title, String content, String status, Long editor) {
        jdbc.update("INSERT INTO pm_product_artifact_version(artifact_id,project_id,version_no,title,content,status,edited_by) VALUES (?,?,?,?,?,?,?)",
                artifactId, projectId, version, title, content, status, editor);
    }

    private Long insertAndReturnKey(String sql, Object... args) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, holder);
        if (holder.getKey() == null) throw new IllegalStateException("未能读取新增记录 ID");
        return holder.getKey().longValue();
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new BusinessException("上下文序列化失败"); }
    }

    private String normalizeTarget(String value) {
        String target = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!APPLY_TARGETS.contains(target)) throw new BusinessException("不支持的应用目标");
        return target;
    }

    private Long number(Object value) { return value instanceof Number n ? n.longValue() : null; }
    private String defaultValue(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT); }
    private <T> List<T> safe(List<T> list) { return list == null ? List.of() : list; }
    private record Access(Long userId, String identity, String permission, boolean viewer, boolean curator) { }
}
