package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.dto.AIProjectPlanDTO;
import com.smartpm.dto.AITaskOptimizationVO;
import com.smartpm.entity.Project;
import com.smartpm.entity.ProjectMember;
import com.smartpm.entity.ProjectMilestone;
import com.smartpm.entity.Task;
import com.smartpm.entity.User;
import com.smartpm.entity.MilestoneTask;
import com.smartpm.mapper.ProjectMapper;
import com.smartpm.mapper.ProjectMemberMapper;
import com.smartpm.mapper.ProjectMilestoneMapper;
import com.smartpm.mapper.TaskMapper;
import com.smartpm.mapper.UserMapper;
import com.smartpm.mapper.MilestoneTaskMapper;
import com.smartpm.service.AIPlanningService;
import com.smartpm.service.AIService;
import com.smartpm.service.ProjectService;
import com.smartpm.service.CollaborationService;
import com.smartpm.service.NotificationService;
import com.smartpm.service.RiskService;
import com.smartpm.service.support.AiTaskDatePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class AIPlanningServiceImpl implements AIPlanningService {

    private static final Set<String> ROLES = Set.of("PROJECT_MANAGER", "PRODUCT_MANAGER", "FRONTEND_DEV", "BACKEND_DEV", "QA_TESTER", "UI_DESIGNER");
    private static final Set<String> PRIORITIES = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> TAGS = Set.of("BUG", "REQUIREMENT", "DESIGN", "DEVELOPMENT", "TESTING", "DOCUMENTATION");

    private final AIService aiService;
    private final ProjectService projectService;
    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final ProjectMilestoneMapper milestoneMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final MilestoneTaskMapper milestoneTaskMapper;
    private final CollaborationService collaborationService;
    private final NotificationService notificationService;
    private final RiskService riskService;

    @Override
    public AIProjectPlanDTO generateProjectPlan(Long projectId) {
        projectService.assertProjectAccess(projectId, true);
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException("项目不存在");
        AIProjectPlanDTO plan = parse(aiService.chat(buildProjectPlanPrompt(project)));
        if (plan.getTasks() == null || plan.getTasks().isEmpty()) {
            throw new BusinessException("AI 未生成有效任务计划，请重试");
        }
        if (plan.getTasks().size() > 20) throw new BusinessException("AI 生成任务过多，请重试");
        normalizePlan(plan);
        return plan;
    }

    @Override
    @Transactional
    public List<Task> applyProjectPlan(Long projectId, AIProjectPlanDTO plan) {
        projectService.assertProjectAccess(projectId, true);
        if (plan == null || plan.getTasks() == null || plan.getTasks().isEmpty()) {
            throw new BusinessException("没有可应用的项目计划");
        }
        Long existing = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId).isNull(Task::getParentId).isNull(Task::getDeletedAt));
        if (existing > 0) throw new BusinessException("项目已有任务，不能重复应用完整计划");
        normalizePlan(plan);
        Map<String, Long> identityUsers = identityUsers(projectId);
        List<Task> created = new ArrayList<>();
        for (int index = 0; index < plan.getTasks().size(); index++) {
            AIProjectPlanDTO.PlanTask item = plan.getTasks().get(index);
            Task task = new Task();
            task.setProjectId(projectId);
            task.setTitle(item.getTitle());
            task.setDescription(item.getDescription());
            task.setStatus("TODO");
            task.setRecommendedRole(item.getRecommendedRole());
            task.setAssigneeId(identityUsers.get(item.getRecommendedRole()));
            task.setPriority(item.getPriority());
            task.setTags(item.getTags());
            task.setStartDate(parseDate(item.getStartDate()));
            task.setDueDate(parseDate(item.getDueDate()));
            AiTaskDatePolicy.apply(task);
            task.setEstimatedHours(item.getEstimatedHours());
            task.setAcceptanceCriteria(item.getAcceptanceCriteria());
            task.setCreatorId(UserHolder.getUserId());
            task.setOrderIndex(index);
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            task.setAiGenerated(true);
            taskMapper.insert(task);
            notificationService.notifyAssignment(projectId, task.getId(), task.getAssigneeId(), task.getTitle());
            created.add(task);
        }
        if (plan.getMilestones() != null) {
            for (AIProjectPlanDTO.PlanMilestone item : plan.getMilestones()) {
                if (item.getName() == null || item.getName().isBlank()) continue;
                ProjectMilestone milestone = new ProjectMilestone();
                milestone.setProjectId(projectId);
                milestone.setName(item.getName().trim());
                milestone.setDescription(item.getDescription());
                milestone.setTargetDate(parseDate(item.getTargetDate()));
                milestone.setStatus("PLANNED");
                List<Long> linkedTaskIds = item.getTaskIndexes() == null ? List.of() : item.getTaskIndexes().stream()
                        .filter(index -> index != null && index >= 0 && index < created.size())
                        .map(index -> created.get(index).getId()).toList();
                milestone.setTaskIds(null);
                milestone.setCreatedAt(LocalDateTime.now());
                milestone.setUpdatedAt(LocalDateTime.now());
                milestoneMapper.insert(milestone);
                linkedTaskIds.forEach(taskId -> milestoneTaskMapper.insert(new MilestoneTask(milestone.getId(), taskId)));
            }
        }
        collaborationService.record(projectId, null, "AI_PLAN_APPLIED", "应用了 AI 完整项目计划，共生成 " + created.size() + " 个任务", null, null);
        riskService.invalidate(projectId);
        return created;
    }

    @Override
    public AITaskOptimizationVO optimizeTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || task.getDeletedAt() != null) throw new BusinessException("任务不存在或已移入回收站");
        projectService.assertProjectAccess(task.getProjectId(), true);
        AITaskOptimizationVO result = parseOptimization(aiService.chat(buildOptimizationPrompt(task)));
        if (result.getTitle() == null || result.getTitle().isBlank() || result.getDescription() == null || result.getDescription().isBlank()) {
            throw new BusinessException("AI 未生成有效优化建议，请重试");
        }
        return result;
    }

    private void normalizePlan(AIProjectPlanDTO plan) {
        for (AIProjectPlanDTO.PlanTask task : plan.getTasks()) {
            if (task.getTitle() == null || task.getTitle().isBlank()) throw new BusinessException("AI 返回的任务标题为空");
            task.setTitle(task.getTitle().trim());
            task.setDescription(blankToDefault(task.getDescription(), "完成该任务对应的功能并通过验收。"));
            String role = upper(task.getRecommendedRole());
            task.setRecommendedRole(ROLES.contains(role) ? role : "PROJECT_MANAGER");
            String priority = upper(task.getPriority());
            task.setPriority(PRIORITIES.contains(priority) ? priority : "MEDIUM");
            task.setTags(normalizeTags(task.getTags()));
            task.setEstimatedHours(task.getEstimatedHours() == null || task.getEstimatedHours() < 1 ? 8 : Math.min(task.getEstimatedHours(), 1000));
            task.setAcceptanceCriteria(blankToDefault(task.getAcceptanceCriteria(), "功能可正常使用，关键流程通过验证。"));
            AiTaskDatePolicy.DateRange dates = AiTaskDatePolicy.resolve(
                    parseDate(task.getStartDate()), parseDate(task.getDueDate()));
            task.setStartDate(dates.startDate().toString());
            task.setDueDate(dates.dueDate().toString());
        }
    }

    private String normalizeTags(String tags) {
        if (tags == null || tags.isBlank()) return "DEVELOPMENT";
        String result = Arrays.stream(tags.split(",")).map(this::upper).filter(TAGS::contains)
                .distinct().collect(Collectors.joining(","));
        return result.isBlank() ? "DEVELOPMENT" : result;
    }

    private Map<String, Long> identityUsers(Long projectId) {
        List<ProjectMember> members = projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId));
        if (members.isEmpty()) return Map.of();
        Map<Long, User> users = userMapper.selectBatchIds(members.stream().map(ProjectMember::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, user -> user));
        Map<String, Long> result = new HashMap<>();
        for (ProjectMember member : members) {
            User user = users.get(member.getUserId());
            String identity = member.getIdentity() != null ? member.getIdentity() : user == null ? null : user.getIdentity();
            if (identity != null) result.putIfAbsent(identity, member.getUserId());
        }
        return result;
    }

    private AIProjectPlanDTO parse(String raw) {
        try { return objectMapper.readValue(extractJson(raw), AIProjectPlanDTO.class); }
        catch (Exception e) { throw new BusinessException("AI 返回的项目计划格式异常，请重试"); }
    }

    private AITaskOptimizationVO parseOptimization(String raw) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(raw));
            if (root.isArray() && !root.isEmpty()) root = root.get(0);
            if (root.has("data") && root.get("data").isObject()) root = root.get("data");
            AITaskOptimizationVO result = new AITaskOptimizationVO();
            result.setTitle(nodeText(root, "title", "optimizedTitle", "optimized_title"));
            result.setDescription(nodeText(root, "description", "optimizedDescription", "optimized_description"));
            result.setAcceptanceCriteria(nodeText(root, "acceptanceCriteria", "acceptance_criteria", "criteria"));
            result.setOversized(nodeBoolean(root, "oversized", "isOversized", "is_oversized", "tooLarge", "too_large"));
            result.setSplitAdvice(nodeText(root, "splitAdvice", "split_advice", "splitSuggestion", "split_suggestion"));
            return result;
        }
        catch (Exception e) { throw new BusinessException("AI 返回的任务优化格式异常，请重试"); }
    }

    private String nodeText(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode node = root.get(name);
            if (node == null || node.isNull()) continue;
            if (node.isArray()) return StreamSupport.stream(node.spliterator(), false)
                    .map(JsonNode::asText).filter(value -> !value.isBlank()).collect(Collectors.joining("；"));
            String value = node.asText();
            if (!value.isBlank()) return value;
        }
        return null;
    }

    private Boolean nodeBoolean(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode node = root.get(name);
            if (node == null || node.isNull()) continue;
            if (node.isBoolean()) return node.booleanValue();
            String value = node.asText().trim().toLowerCase(Locale.ROOT);
            if (Set.of("true", "yes", "是", "需要").contains(value)) return true;
            if (Set.of("false", "no", "否", "不需要").contains(value)) return false;
        }
        return false;
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) throw new BusinessException("AI 未返回内容，请重试");
        Matcher fenced = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```").matcher(raw);
        if (fenced.find()) return fenced.group(1).trim();
        int start = raw.indexOf('{'), end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) throw new BusinessException("AI 返回内容中未找到 JSON，请重试");
        return raw.substring(start, end + 1);
    }

    private String buildProjectPlanPrompt(Project project) {
        return "你是一名资深技术项目经理。请为项目生成一份可落地的完整开发计划。\n"
                + "项目名称：" + project.getName() + "\n项目描述：" + blankToDefault(project.getDescription(), "未提供") + "\n"
                + "今天日期：" + LocalDate.now() + "\n"
                + "只返回 JSON 对象，不要 Markdown。字段严格为 overview、stages、tasks、milestones、risks。\n"
                + "stages: [{name,startDate,endDate,goal}]；tasks: [{stage,title,description,recommendedRole,priority,tags,startDate,dueDate,estimatedHours,acceptanceCriteria}]；"
                + "milestones: [{name,description,targetDate,taskIndexes}]；risks: [{level,title,description,mitigation}]。\n"
                + "任务 5-12 个，日期格式 yyyy-MM-dd；recommendedRole 只能为 PROJECT_MANAGER、PRODUCT_MANAGER、FRONTEND_DEV、BACKEND_DEV、QA_TESTER、UI_DESIGNER；priority 只能为 HIGH、MEDIUM、LOW；"
                + "tags 使用 BUG、REQUIREMENT、DESIGN、DEVELOPMENT、TESTING、DOCUMENTATION 的逗号组合；taskIndexes 使用 tasks 数组从 0 开始的索引。"
                + "每个任务必须包含可测试的 acceptanceCriteria，并按真实依赖顺序安排。";
    }

    private String buildOptimizationPrompt(Task task) {
        return "你是一名资深技术项目经理。请优化以下任务，使其可执行、可验收。\n"
                + "任务标题：" + task.getTitle() + "\n任务描述：" + blankToDefault(task.getDescription(), "未提供") + "\n"
                + "现有验收条件：" + blankToDefault(task.getAcceptanceCriteria(), "未提供") + "\n"
                + "只返回 JSON 对象，不要 Markdown。字段严格为 title、description、acceptanceCriteria、oversized、splitAdvice。"
                + "description 应说明范围、关键动作和交付物；acceptanceCriteria 应可验证；oversized 是布尔值；如果任务过大，splitAdvice 给出 3-5 个建议拆分步骤。";
    }

    private String upper(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private String blankToDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private LocalDate parseDate(String value) { try { return value == null || value.isBlank() ? null : LocalDate.parse(value); } catch (Exception ignored) { return null; } }
}
