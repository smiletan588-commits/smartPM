package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.dto.DragDTO;
import com.smartpm.dto.BatchTaskUpdateDTO;
import com.smartpm.dto.TaskUpdateDTO;
import com.smartpm.entity.Project;
import com.smartpm.entity.ProjectMember;
import com.smartpm.entity.Task;
import com.smartpm.entity.User;
import com.smartpm.mapper.ProjectMapper;
import com.smartpm.mapper.ProjectMemberMapper;
import com.smartpm.mapper.TaskMapper;
import com.smartpm.mapper.TaskDependencyMapper;
import com.smartpm.mapper.UserMapper;
import com.smartpm.service.AIService;
import com.smartpm.service.ProjectService;
import com.smartpm.service.TaskService;
import com.smartpm.service.CollaborationService;
import com.smartpm.service.NotificationService;
import com.smartpm.service.RiskService;
import com.smartpm.service.support.AiTaskDatePolicy;
import com.smartpm.service.support.TaskWorkflowRules;
import com.smartpm.entity.TaskDependency;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private static final Set<String> VALID_PRIORITIES = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> VALID_TAGS = Set.of("BUG", "REQUIREMENT", "DESIGN", "DEVELOPMENT", "TESTING", "DOCUMENTATION");

    private final TaskMapper taskMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserMapper userMapper;
    private final AIService aiService;
    private final ProjectService projectService;
    private final TaskDependencyMapper dependencyMapper;
    private final CollaborationService collaborationService;
    private final NotificationService notificationService;
    private final RiskService riskService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 创建主任务 ──

    @Override
    @Transactional
    public Task create(Long projectId, String title, String description, Long assigneeId, String dueDate,
                       String startDate, String priority, String tags, String dependencyIds,
                       Integer estimatedHours, Integer actualHours, String acceptanceCriteria) {
        projectService.assertProjectAccess(projectId, true);
        if (title == null || title.isBlank()) {
            throw new BusinessException("任务标题不能为空");
        }
        if (title.trim().length() > 255) {
            throw new BusinessException("任务标题不能超过 255 字");
        }

        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }

        Task task = new Task();
        task.setProjectId(projectId);
        task.setTitle(title.trim());
        task.setDescription(description);
        task.setStatus("TODO");
        if (assigneeId != null) assertProjectMember(projectId, project, assigneeId);
        task.setAssigneeId(assigneeId);
        task.setPriority(normalizePriority(priority));
        task.setTags(normalizeTags(tags));
        String normalizedDependencies = normalizeDependencyIds(projectId, null, dependencyIds);
        task.setDependencyIds(null);
        if (dueDate != null && !dueDate.isBlank()) {
            task.setDueDate(parseDate(dueDate, "截止日期"));
        }
        if (startDate != null && !startDate.isBlank()) {
            task.setStartDate(parseDate(startDate, "开始日期"));
        }
        validateDates(task.getStartDate(), task.getDueDate());
        task.setEstimatedHours(normalizeHours(estimatedHours, "预计工时"));
        task.setActualHours(normalizeHours(actualHours, "实际工时"));
        task.setAcceptanceCriteria(acceptanceCriteria);
        task.setReviewRequired(false);
        task.setAcceptanceStatus("NOT_REQUIRED");
        task.setCreatorId(UserHolder.getUserId());
        task.setOrderIndex(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setAiGenerated(false);

        taskMapper.insert(task);
        replaceDependencies(task.getId(), normalizedDependencies);
        task.setDependencyIds(normalizedDependencies);
        collaborationService.record(projectId, task.getId(), "TASK_CREATED", "创建了任务", null,
                Map.of("title", task.getTitle(), "status", task.getStatus()));
        notificationService.notifyAssignment(projectId, task.getId(), assigneeId, task.getTitle());
        riskService.invalidate(projectId);
        return task;
    }

    // ── 查询看板任务（仅主任务，parent_id IS NULL）──

    @Override
    public List<Task> listByProject(Long projectId) {
        projectService.assertProjectAccess(projectId, false);
        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, projectId)
                        .isNull(Task::getParentId)
                        .isNull(Task::getDeletedAt)
                        .orderByAsc(Task::getOrderIndex)
                        .orderByDesc(Task::getCreatedAt));
        hydrateDependencies(tasks);
        applyBlockedStates(projectId, tasks);
        return tasks;
    }

    // ── 查询子任务列表 ──

    @Override
    public List<Task> listSubtasks(Long taskId) {
        Task task = requireActiveTask(taskId);
        projectService.assertProjectAccess(task.getProjectId(), false);
        List<Task> subtasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getParentId, taskId)
                        .isNull(Task::getDeletedAt)
                        .orderByAsc(Task::getCreatedAt));
        hydrateDependencies(subtasks);
        applyBlockedStates(task.getProjectId(), subtasks);
        return subtasks;
    }

    // ── 更新任务 ──

    @Override
    @Transactional
    public Task update(TaskUpdateDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("任务ID不能为空");
        }

        Task task = requireActiveTask(dto.getId());
        projectService.assertProjectAccess(task.getProjectId(), true);
        String oldStatus = task.getStatus();
        Long oldAssigneeId = task.getAssigneeId();
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("title", task.getTitle());
        before.put("status", oldStatus);
        before.put("assigneeId", oldAssigneeId);

        if (dto.getTitle() != null) {
            if (dto.getTitle().isBlank()) throw new BusinessException("任务标题不能为空");
            task.setTitle(dto.getTitle().trim());
        }
        if (dto.getDescription() != null) {
            task.setDescription(dto.getDescription());
        }
        if (dto.getStatus() != null) {
            String status = dto.getStatus().toUpperCase();
            if (!status.equals("TODO") && !status.equals("IN_PROGRESS") && !status.equals("DONE")) {
                throw new BusinessException("无效的任务状态: " + dto.getStatus());
            }
            assertAcceptanceAllowsDone(task, status);
            TaskWorkflowRules.assertTransition(oldStatus, status);
            task.setStatus(status);
            if ("DONE".equals(status) && !"DONE".equals(oldStatus)) task.setCompletedAt(LocalDateTime.now());
            if (!"DONE".equals(status) && "DONE".equals(oldStatus)) task.setCompletedAt(null);
        }
        if (Boolean.TRUE.equals(dto.getClearAssignee())) {
            assertCanChangeAssignee(task, null);
            task.setAssigneeId(null);
        } else if (dto.getAssigneeId() != null && !Objects.equals(task.getAssigneeId(), dto.getAssigneeId())) {
            assertCanChangeAssignee(task, dto.getAssigneeId());
            task.setAssigneeId(dto.getAssigneeId());
        }
        if ("TODO".equals(oldStatus) && "IN_PROGRESS".equals(task.getStatus()) && task.getAssigneeId() == null) {
            task.setAssigneeId(UserHolder.getUserId());
        }
        if (dto.getDueDate() != null) {
            task.setDueDate(dto.getDueDate().isBlank() ? null : parseDate(dto.getDueDate(), "截止日期"));
        }
        if (dto.getStartDate() != null) {
            task.setStartDate(dto.getStartDate().isBlank() ? null : parseDate(dto.getStartDate(), "开始日期"));
        }
        validateDates(task.getStartDate(), task.getDueDate());
        if (dto.getPriority() != null) {
            task.setPriority(normalizePriority(dto.getPriority()));
        }
        if (dto.getTags() != null) {
            task.setTags(normalizeTags(dto.getTags()));
        }
        String normalizedDependencies = dto.getDependencyIds() == null ? null
                : normalizeDependencyIds(task.getProjectId(), task.getId(), dto.getDependencyIds());
        if (dto.getEstimatedHours() != null) {
            task.setEstimatedHours(normalizeHours(dto.getEstimatedHours(), "预计工时"));
        }
        if (dto.getActualHours() != null) {
            task.setActualHours(normalizeHours(dto.getActualHours(), "实际工时"));
        }
        if (dto.getAcceptanceCriteria() != null) {
            task.setAcceptanceCriteria(dto.getAcceptanceCriteria());
        }
        if (dto.getReviewRequired() != null) {
            task.setReviewRequired(dto.getReviewRequired());
            task.setAcceptanceStatus(Boolean.TRUE.equals(dto.getReviewRequired()) ? "NOT_READY" : "NOT_REQUIRED");
            task.setAcceptanceSubmittedAt(null);
        }
        if (!"TODO".equals(task.getStatus())) {
            assertDependenciesCompleted(task);
        }
        if (dto.getOrderIndex() != null) {
            task.setOrderIndex(dto.getOrderIndex());
        }
        task.setUpdatedAt(LocalDateTime.now());

        taskMapper.updateById(task);
        if (dto.getDependencyIds() != null) replaceDependencies(task.getId(), normalizedDependencies);
        task.setDependencyIds(dto.getDependencyIds() != null ? normalizedDependencies : serializeDependencies(task.getId()));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("title", task.getTitle());
        after.put("status", task.getStatus());
        after.put("assigneeId", task.getAssigneeId());
        String action = !Objects.equals(oldStatus, task.getStatus()) ? "STATUS_CHANGED"
                : !Objects.equals(oldAssigneeId, task.getAssigneeId()) ? "ASSIGNEE_CHANGED" : "TASK_UPDATED";
        collaborationService.record(task.getProjectId(), task.getId(), action, activitySummary(action, task), before, after);
        if (!Objects.equals(oldAssigneeId, task.getAssigneeId())) {
            notificationService.notifyAssignment(task.getProjectId(), task.getId(), task.getAssigneeId(), task.getTitle());
        }
        riskService.invalidate(task.getProjectId());
        return task;
    }

    @Override
    @Transactional
    public List<Task> batchUpdate(BatchTaskUpdateDTO dto) {
        List<Long> ids = dto.getTaskIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) throw new BusinessException("请选择要修改的任务");
        if (ids.size() > 100) throw new BusinessException("单次最多批量修改 100 个任务");
        if (dto.getStatus() == null && dto.getAssigneeId() == null && !Boolean.TRUE.equals(dto.getClearAssignee())
                && dto.getPriority() == null && dto.getDueDate() == null) {
            throw new BusinessException("至少提供一个批量修改项");
        }
        List<Task> source = taskMapper.selectBatchIds(ids).stream().filter(t -> t.getDeletedAt() == null).toList();
        if (source.size() != ids.size()) throw new BusinessException("部分任务不存在或已移入回收站");
        Long projectId = source.get(0).getProjectId();
        if (source.stream().anyMatch(t -> !Objects.equals(projectId, t.getProjectId()))) {
            throw new BusinessException("批量修改只能选择同一项目的任务");
        }
        projectService.assertProjectAccess(projectId, true);
        List<Task> updated = new ArrayList<>();
        for (Task item : source) {
            TaskUpdateDTO patch = new TaskUpdateDTO();
            patch.setId(item.getId());
            patch.setStatus(dto.getStatus());
            patch.setAssigneeId(dto.getAssigneeId());
            patch.setClearAssignee(dto.getClearAssignee());
            patch.setPriority(dto.getPriority());
            patch.setDueDate(dto.getDueDate());
            updated.add(update(patch));
        }
        return updated;
    }

    /**
     * 项目负责人可以改派或清空负责人；普通成员只允许接取尚未分配的任务，
     * 且只能指派给自己。所有被指派者都必须属于当前项目。
     */
    private void assertCanChangeAssignee(Task task, Long newAssigneeId) {
        Project project = projectMapper.selectById(task.getProjectId());
        Long currentUserId = UserHolder.getUserId();
        boolean isProjectOwner = project != null && Objects.equals(project.getCreatorId(), currentUserId);

        if (isProjectOwner) {
            if (newAssigneeId != null) {
                assertProjectMember(task.getProjectId(), project, newAssigneeId);
            }
            return;
        }

        boolean isSelfClaim = task.getAssigneeId() == null
                && newAssigneeId != null
                && Objects.equals(newAssigneeId, currentUserId);
        if (!isSelfClaim) {
            throw new BusinessException("只有项目负责人可以改派或清空任务负责人");
        }
    }

    private void assertProjectMember(Long projectId, Project project, Long userId) {
        if (Objects.equals(project.getCreatorId(), userId)) return;
        Long memberCount = projectMemberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId));
        if (memberCount == 0) {
            throw new BusinessException("只能将任务指派给当前项目成员");
        }
    }

    // ── 拖拽排序（仅操作主任务，忽略子任务）──

    @Override
    @Transactional
    public void drag(DragDTO dto) {
        if (dto.getTaskId() == null) {
            throw new BusinessException("任务ID不能为空");
        }
        if (dto.getTargetStatus() == null || dto.getTargetStatus().isBlank()) {
            throw new BusinessException("目标状态不能为空");
        }

        String targetStatus = dto.getTargetStatus().toUpperCase();
        if (!targetStatus.equals("TODO") && !targetStatus.equals("IN_PROGRESS") && !targetStatus.equals("DONE")) {
            throw new BusinessException("无效的任务状态: " + dto.getTargetStatus());
        }

        Task task = requireActiveTask(dto.getTaskId());
        String sourceStatus = task.getStatus();

        assertAcceptanceAllowsDone(task, targetStatus);
        TaskWorkflowRules.assertTransition(sourceStatus, targetStatus);

        Long projectId = task.getProjectId();
        projectService.assertProjectAccess(projectId, true);
        if (!"TODO".equals(targetStatus)) {
            assertDependenciesCompleted(task);
        }
        int sourceOrder = task.getOrderIndex() != null ? task.getOrderIndex() : 0;

        // 目标列主任务数量（排除子任务）
        Long maxOrder = taskMapper.selectCount(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, projectId)
                        .eq(Task::getStatus, targetStatus)
                        .isNull(Task::getParentId)
                        .isNull(Task::getDeletedAt));
        int maxIndex = maxOrder.intValue();
        int targetOrder = dto.getTargetOrderIndex() != null ? dto.getTargetOrderIndex() : maxIndex;
        if (targetOrder < 0) targetOrder = 0;
        if (targetOrder > maxIndex) targetOrder = maxIndex;

        if (sourceStatus.equals(targetStatus)) {
            if (sourceOrder == targetOrder) return;

            if (sourceOrder < targetOrder) {
                taskMapper.update(null,
                        new LambdaUpdateWrapper<Task>()
                                .eq(Task::getProjectId, projectId)
                                .eq(Task::getStatus, sourceStatus)
                                .isNull(Task::getParentId)
                                .isNull(Task::getDeletedAt)
                                .gt(Task::getOrderIndex, sourceOrder)
                                .le(Task::getOrderIndex, targetOrder)
                                .setSql("order_index = order_index - 1"));
            } else {
                taskMapper.update(null,
                        new LambdaUpdateWrapper<Task>()
                                .eq(Task::getProjectId, projectId)
                                .eq(Task::getStatus, sourceStatus)
                                .isNull(Task::getParentId)
                                .isNull(Task::getDeletedAt)
                                .ge(Task::getOrderIndex, targetOrder)
                                .lt(Task::getOrderIndex, sourceOrder)
                                .setSql("order_index = order_index + 1"));
            }
        } else {
            taskMapper.update(null,
                    new LambdaUpdateWrapper<Task>()
                            .eq(Task::getProjectId, projectId)
                            .eq(Task::getStatus, sourceStatus)
                            .isNull(Task::getParentId)
                            .isNull(Task::getDeletedAt)
                            .gt(Task::getOrderIndex, sourceOrder)
                            .setSql("order_index = order_index - 1"));

            taskMapper.update(null,
                    new LambdaUpdateWrapper<Task>()
                            .eq(Task::getProjectId, projectId)
                            .eq(Task::getStatus, targetStatus)
                            .isNull(Task::getParentId)
                            .isNull(Task::getDeletedAt)
                            .ge(Task::getOrderIndex, targetOrder)
                            .setSql("order_index = order_index + 1"));
        }

        task.setStatus(targetStatus);
        task.setOrderIndex(targetOrder);
        task.setUpdatedAt(LocalDateTime.now());
        Long oldAssigneeId = task.getAssigneeId();
        if ("TODO".equals(sourceStatus) && "IN_PROGRESS".equals(targetStatus) && oldAssigneeId == null) {
            task.setAssigneeId(UserHolder.getUserId());
        }
        if ("DONE".equals(targetStatus) && !"DONE".equals(sourceStatus)) task.setCompletedAt(LocalDateTime.now());
        if (!"DONE".equals(targetStatus) && "DONE".equals(sourceStatus)) task.setCompletedAt(null);
        taskMapper.updateById(task);
        collaborationService.record(projectId, task.getId(), sourceStatus.equals(targetStatus) ? "TASK_REORDERED" : "STATUS_CHANGED",
                sourceStatus.equals(targetStatus) ? "调整了任务顺序" : "将任务状态从 " + sourceStatus + " 改为 " + targetStatus,
                Map.of("status", sourceStatus, "orderIndex", sourceOrder), Map.of("status", targetStatus, "orderIndex", targetOrder));
        if (!Objects.equals(oldAssigneeId, task.getAssigneeId())) {
            notificationService.notifyAssignment(projectId, task.getId(), task.getAssigneeId(), task.getTitle());
        }
        riskService.invalidate(projectId);
    }

    // ── 删除任务（级联删除子任务）──

    @Override
    @Transactional
    public void delete(Long id) {
        Task task = requireActiveTask(id);
        projectService.assertProjectAccess(task.getProjectId(), true);
        assertNoTaskDependsOn(task);
        LocalDateTime now = LocalDateTime.now();
        task.setDeletedAt(now); task.setDeletedBy(UserHolder.getUserId()); task.setUpdatedAt(now);
        taskMapper.updateById(task);
        taskMapper.update(null, new LambdaUpdateWrapper<Task>().eq(Task::getParentId, id).isNull(Task::getDeletedAt)
                .set(Task::getDeletedAt, now).set(Task::getDeletedBy, UserHolder.getUserId()).set(Task::getUpdatedAt, now));
        collaborationService.record(task.getProjectId(), task.getId(), "TASK_DELETED", "将任务移入回收站", null, null);
        riskService.invalidate(task.getProjectId());
    }

    // ── 切换子任务完成状态 ──

    @Override
    @Transactional
    public Task toggleSubtask(Long taskId) {
        Task task = requireActiveTask(taskId);
        projectService.assertProjectAccess(task.getProjectId(), true);
        if (task.getParentId() == null) {
            throw new BusinessException("该任务为主任务，不支持此操作");
        }
        String newStatus = "DONE".equals(task.getStatus()) ? "TODO" : "DONE";
        assertAcceptanceAllowsDone(task, newStatus);
        if (!"TODO".equals(newStatus)) {
            assertDependenciesCompleted(task);
        }
        task.setStatus(newStatus);
        task.setUpdatedAt(LocalDateTime.now());
        task.setCompletedAt("DONE".equals(newStatus) ? LocalDateTime.now() : null);
        taskMapper.updateById(task);
        collaborationService.record(task.getProjectId(), task.getId(), "STATUS_CHANGED",
                "将子任务状态改为 " + newStatus, null, Map.of("status", newStatus));
        riskService.invalidate(task.getProjectId());
        return task;
    }

    private void assertAcceptanceAllowsDone(Task task, String targetStatus) {
        if ("DONE".equals(targetStatus) && Boolean.TRUE.equals(task.getReviewRequired())
                && !"PASSED".equals(task.getAcceptanceStatus())) {
            throw new BusinessException("该任务需要先提交并通过验收，不能直接完成");
        }
    }

    // ── AI 任务拆解 ──

    @Override
    @Transactional
    public List<Task> decomposeTask(Long taskId) {
        Task task = requireActiveTask(taskId);
        projectService.assertProjectAccess(task.getProjectId(), true);

        // 查询项目成员及其项目内岗位，用于自动分派。
        Map<String, Long> identityUserMap = buildIdentityUserMap(task.getProjectId());

        String prompt = buildDecomposePrompt(task.getTitle(), task.getDescription());
        String aiResponse = aiService.chat(prompt);
        List<Map<String, String>> subtaskMaps = parseSubtaskJson(aiResponse);

        if (subtaskMaps.isEmpty()) {
            throw new BusinessException("AI 未能生成有效的子任务，请重试");
        }

        List<Task> created = new ArrayList<>();
        for (Map<String, String> st : subtaskMaps) {
            Task sub = new Task();
            sub.setProjectId(task.getProjectId());
            sub.setParentId(taskId);
            sub.setTitle(st.get("title"));
            sub.setDescription(st.getOrDefault("description", ""));
            sub.setStatus("TODO");
            sub.setPriority("MEDIUM");
            sub.setTags("DEVELOPMENT");
            sub.setCreatorId(UserHolder.getUserId());
            sub.setOrderIndex(0);
            String recommendedRole = st.get("recommended_role");
            sub.setRecommendedRole(recommendedRole);
            AiTaskDatePolicy.apply(sub);

            // 自动指派：按推荐角色匹配项目成员
            if (recommendedRole != null && identityUserMap.containsKey(recommendedRole)) {
                sub.setAssigneeId(identityUserMap.get(recommendedRole));
            }

            sub.setCreatedAt(LocalDateTime.now());
            sub.setUpdatedAt(LocalDateTime.now());
            sub.setAiGenerated(true);
            taskMapper.insert(sub);
            created.add(sub);
        }
        collaborationService.record(task.getProjectId(), taskId, "AI_DECOMPOSED", "AI 生成了 " + created.size() + " 个子任务", null, null);
        riskService.invalidate(task.getProjectId());
        return created;
    }

    /**
     * 构造拆解任务的 Prompt。
     * 关键约束：
     * 1. 子任务标题绝对不能与主任务标题相同或高度重叠
     * 2. 子任务必须是具体的技术性行动步骤，而非抽象描述
     * 3. 强调输出格式的严格性
     */
    private String buildDecomposePrompt(String title, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个资深技术项目经理。请将以下任务拆解为3-5个具体的、可独立执行的子任务。\n\n");
        sb.append("【任务标题】\n").append(title).append("\n\n");
        if (description != null && !description.isBlank()) {
            sb.append("【任务描述】\n").append(description).append("\n\n");
        }
        sb.append("【团队配置 — 请根据角色合理分配】\n");
        sb.append("系统支持以下六种专业身份，请在拆解时为每个子任务指定最合适的负责人角色：\n");
        sb.append("- PROJECT_MANAGER  (项目经理)：需求分析、流程规划、进度管控、风险管理\n");
        sb.append("- PRODUCT_MANAGER  (产品经理)：用户研究、需求定义、PRD、验收标准、版本规划\n");
        sb.append("- FRONTEND_DEV    (前端工程师)：UI实现、页面交互、组件开发、前端联调\n");
        sb.append("- BACKEND_DEV     (后端工程师)：数据库设计、API开发、业务逻辑、系统架构\n");
        sb.append("- QA_TESTER       (测试工程师)：测试用例、功能测试、回归测试、质量报告\n");
        sb.append("- UI_DESIGNER     (UI设计师)：视觉设计、交互原型、设计规范、品牌风格\n\n");
        sb.append("【拆解要求 — 请严格遵守】\n");
        sb.append("1. 每个子任务必须是具体的、技术层面的动作，而不是抽象的概念复述\n");
        sb.append("2. 子任务之间要有清晰的逻辑先后顺序（先设计→再开发→最后联调）\n");
        sb.append("3. 子任务数量控制在3-5个，宁少勿滥\n");
        sb.append("4. 【极其重要】子任务的标题绝对不能与主任务标题「").append(title).append("」相同或高度相似！\n");
        sb.append("5. 为每个子任务指定最合适的 recommended_role（必须从上述六种身份中选择）\n\n");
        sb.append("【输出格式】\n");
        sb.append("只返回一个JSON数组，不要加任何其他文字、解释或Markdown标记。每个元素必须包含 title、description、recommended_role 三个字段：\n");
        sb.append("[{\"title\": \"子任务标题\", \"description\": \"具体做什么\", \"recommended_role\": \"角色代码\"}]\n\n");
        sb.append("示例（主任务=实现用户登录）：\n");
        sb.append("[{\"title\": \"设计用户表结构\", \"description\": \"确定用户表的字段、索引和密码加密方案\", \"recommended_role\": \"BACKEND_DEV\"}, {\"title\": \"编写登录认证接口\", \"description\": \"实现POST /login接口，含参数校验和JWT签发\", \"recommended_role\": \"BACKEND_DEV\"}, {\"title\": \"实现前端登录页面\", \"description\": \"编写登录表单UI及前端表单验证逻辑\", \"recommended_role\": \"FRONTEND_DEV\"}, {\"title\": \"前后端联调登录流程\", \"description\": \"对接登录接口，处理token存储和异常情况\", \"recommended_role\": \"FRONTEND_DEV\"}]");
        return sb.toString();
    }

    /**
     * 从 AI 返回的文本中提取 JSON 数组并解析。
     */
    private List<Map<String, String>> parseSubtaskJson(String raw) {
        if (raw == null || raw.isBlank()) return List.of();

        Pattern fencePattern = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");
        Matcher fenceMatcher = fencePattern.matcher(raw);
        if (fenceMatcher.find()) {
            raw = fenceMatcher.group(1).trim();
        }

        Pattern arrayPattern = Pattern.compile("\\[[\\s\\S]*\\]");
        Matcher arrayMatcher = arrayPattern.matcher(raw);
        if (!arrayMatcher.find()) {
            throw new BusinessException("AI 返回内容中未找到 JSON 数组，请重试");
        }

        String json = arrayMatcher.group();

        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            throw new BusinessException("AI 返回的 JSON 格式异常: " + e.getMessage());
        }
    }

    // ── AI 一键生成项目初始任务 ──

    @Override
    @Transactional
    public List<Task> initTasks(Long projectId) {
        projectService.assertProjectAccess(projectId, true);
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) throw new BusinessException("项目不存在");

        // 检查是否已有任务
        Long existing = taskMapper.selectCount(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, projectId)
                        .isNull(Task::getParentId).isNull(Task::getDeletedAt));
        if (existing > 0) {
            throw new BusinessException("该项目已有任务，初始化仅适用于空项目");
        }

        String prompt = buildInitPrompt(project.getName(), project.getDescription());
        String aiResponse = aiService.chat(prompt);
        List<Map<String, String>> taskMaps = parseSubtaskJson(aiResponse);

        if (taskMaps.isEmpty()) {
            throw new BusinessException("AI 未能生成有效任务，请重试");
        }
        if (taskMaps.size() > 8) {
            throw new BusinessException("AI 生成任务过多，请重试");
        }

        List<Task> created = new ArrayList<>();
        int order = 0;
        for (Map<String, String> tm : taskMaps) {
            String title = tm.get("title");
            if (title == null || title.isBlank()) continue;
            Task task = new Task();
            task.setProjectId(projectId);
            task.setParentId(null);
            task.setTitle(title.trim());
            task.setDescription(tm.getOrDefault("description", ""));
            task.setStatus("TODO");
            task.setPriority("MEDIUM");
            task.setTags("DEVELOPMENT");
            task.setCreatorId(UserHolder.getUserId());
            task.setOrderIndex(order++);
            String recommendedRole = tm.get("recommended_role");
            task.setRecommendedRole(recommendedRole);
            AiTaskDatePolicy.apply(task);
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            task.setAiGenerated(true);
            taskMapper.insert(task);
            created.add(task);
        }
        if (created.isEmpty()) {
            throw new BusinessException("AI 未生成有效的开发任务，请重试");
        }
        collaborationService.record(projectId, null, "AI_PLAN_APPLIED", "AI 初始化了 " + created.size() + " 个项目任务", null, null);
        riskService.invalidate(projectId);
        return created;
    }

    private String normalizePriority(String priority) {
        String value = priority == null || priority.isBlank() ? "MEDIUM" : priority.trim().toUpperCase(Locale.ROOT);
        if (!VALID_PRIORITIES.contains(value)) {
            throw new BusinessException("无效的任务优先级");
        }
        return value;
    }

    private String normalizeTags(String tags) {
        if (tags == null || tags.isBlank()) return null;
        LinkedHashSet<String> values = Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!VALID_TAGS.containsAll(values)) {
            throw new BusinessException("任务标签包含不支持的类型");
        }
        return values.isEmpty() ? null : String.join(",", values);
    }

    private String normalizeDependencyIds(Long projectId, Long taskId, String dependencyIds) {
        if (dependencyIds == null || dependencyIds.isBlank()) return null;
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        try {
            for (String rawId : dependencyIds.split(",")) {
                if (!rawId.isBlank()) ids.add(Long.valueOf(rawId.trim()));
            }
        } catch (NumberFormatException e) {
            throw new BusinessException("前置任务格式无效");
        }
        if (taskId != null && ids.contains(taskId)) {
            throw new BusinessException("任务不能依赖自身");
        }
        if (ids.isEmpty()) return null;
        List<Task> dependencies = taskMapper.selectList(new LambdaQueryWrapper<Task>().in(Task::getId, ids).isNull(Task::getDeletedAt));
        if (dependencies.size() != ids.size() || dependencies.stream().anyMatch(task -> !projectId.equals(task.getProjectId()))) {
            throw new BusinessException("前置任务不存在或不属于当前项目");
        }
        if (taskId != null && ids.stream().anyMatch(id -> dependencyReaches(id, taskId, new HashSet<>()))) {
            throw new BusinessException("前置依赖不能形成循环");
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private Integer normalizeHours(Integer hours, String fieldName) {
        if (hours != null && (hours < 0 || hours > 10000)) {
            throw new BusinessException(fieldName + "应在 0 到 10000 小时之间");
        }
        return hours;
    }

    private void validateDates(LocalDate startDate, LocalDate dueDate) {
        if (startDate != null && dueDate != null && startDate.isAfter(dueDate)) {
            throw new BusinessException("开始日期不能晚于截止日期");
        }
    }

    private LocalDate parseDate(String value, String fieldName) {
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            throw new BusinessException(fieldName + "格式无效");
        }
    }

    private List<Long> dependencyIdList(Task task) {
        return dependencyMapper.selectList(new LambdaQueryWrapper<TaskDependency>()
                        .eq(TaskDependency::getTaskId, task.getId()))
                .stream().map(TaskDependency::getPrerequisiteTaskId).toList();
    }

    private boolean dependencyReaches(Long currentId, Long targetId, Set<Long> visited) {
        if (!visited.add(currentId)) return false;
        if (currentId.equals(targetId)) return true;
        Task current = taskMapper.selectById(currentId);
        if (current == null || current.getDeletedAt() != null) return false;
        return dependencyIdList(current).stream().anyMatch(id -> dependencyReaches(id, targetId, visited));
    }

    private void assertDependenciesCompleted(Task task) {
        List<Long> ids = dependencyIdList(task);
        if (ids.isEmpty()) return;
        List<Task> dependencies = taskMapper.selectList(new LambdaQueryWrapper<Task>().in(Task::getId, ids).isNull(Task::getDeletedAt));
        List<String> unfinished = dependencies.stream()
                .filter(dependency -> !"DONE".equals(dependency.getStatus()))
                .map(Task::getTitle)
                .toList();
        if (!unfinished.isEmpty()) {
            throw new BusinessException("任务被前置任务阻塞：" + String.join("、", unfinished));
        }
    }

    private void applyBlockedStates(Long projectId, List<Task> tasks) {
        if (tasks.isEmpty()) return;
        Map<Long, Task> taskMap = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, projectId).isNull(Task::getDeletedAt))
                .stream().collect(Collectors.toMap(Task::getId, task -> task));
        for (Task task : tasks) {
            List<String> blockedBy = dependencyIdList(task).stream()
                    .map(taskMap::get)
                    .filter(Objects::nonNull)
                    .filter(dependency -> !"DONE".equals(dependency.getStatus()))
                    .map(Task::getTitle)
                    .toList();
            task.setBlocked(!blockedBy.isEmpty());
            task.setBlockedByTaskTitles(blockedBy);
        }
    }

    private void assertNoTaskDependsOn(Task task) {
        List<Long> dependentIds = dependencyMapper.selectList(new LambdaQueryWrapper<TaskDependency>()
                        .eq(TaskDependency::getPrerequisiteTaskId, task.getId()))
                .stream().map(TaskDependency::getTaskId).toList();
        List<String> dependents = dependentIds.isEmpty() ? List.of() : taskMapper.selectList(new LambdaQueryWrapper<Task>()
                        .in(Task::getId, dependentIds).isNull(Task::getDeletedAt))
                .stream().map(Task::getTitle).toList();
        if (!dependents.isEmpty()) {
            throw new BusinessException("该任务被以下任务依赖，无法删除：" + String.join("、", dependents));
        }
    }

    private void replaceDependencies(Long taskId, String dependencyIds) {
        dependencyMapper.delete(new LambdaQueryWrapper<TaskDependency>().eq(TaskDependency::getTaskId, taskId));
        if (dependencyIds == null || dependencyIds.isBlank()) return;
        Arrays.stream(dependencyIds.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                .map(Long::valueOf).forEach(id -> dependencyMapper.insert(new TaskDependency(taskId, id)));
    }

    private String serializeDependencies(Long taskId) {
        List<Long> ids = dependencyMapper.selectList(new LambdaQueryWrapper<TaskDependency>()
                        .eq(TaskDependency::getTaskId, taskId))
                .stream().map(TaskDependency::getPrerequisiteTaskId).toList();
        return ids.isEmpty() ? null : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private void hydrateDependencies(List<Task> tasks) {
        tasks.forEach(task -> task.setDependencyIds(serializeDependencies(task.getId())));
    }

    private String activitySummary(String action, Task task) {
        return switch (action) {
            case "STATUS_CHANGED" -> "将任务状态改为 " + task.getStatus();
            case "ASSIGNEE_CHANGED" -> "调整了任务负责人";
            default -> "更新了任务信息";
        };
    }

    /** 根据项目成员的专业身份建立自动指派映射。 */
    private Map<String, Long> buildIdentityUserMap(Long projectId) {
        List<ProjectMember> members = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId));
        Map<String, Long> identityUserMap = new java.util.HashMap<>();
        if (members.isEmpty()) return identityUserMap;

        List<Long> userIds = members.stream().map(ProjectMember::getUserId).toList();
        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, user -> user));
        for (ProjectMember member : members) {
            User user = users.get(member.getUserId());
            String identity = member.getIdentity() != null && !member.getIdentity().isBlank()
                    ? member.getIdentity()
                    : user != null ? user.getIdentity() : null;
            if (identity != null && !identity.isBlank()) {
                identityUserMap.putIfAbsent(identity, member.getUserId());
            }
        }
        return identityUserMap;
    }

    private String buildInitPrompt(String projectName, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个资深技术项目经理和敏捷教练。\n\n");
        sb.append("请分析以下项目，并为其制定一份核心开发路径，包含 3 到 5 个必须优先执行的阶段性大任务。\n\n");
        sb.append("【项目名称】\n").append(projectName).append("\n\n");
        if (description != null && !description.isBlank()) {
            sb.append("【项目描述】\n").append(description).append("\n\n");
        }
        sb.append("【任务规划要求 — 请严格遵守】\n");
        sb.append("1. 每个任务必须是独立完整的开发阶段，有明确的边界和可交付成果\n");
        sb.append("2. 任务之间按依赖关系排序（先基础设施→再核心功能→最后测试验收）\n");
        sb.append("3. 任务数量控制在 3-5 个，每个任务描述控制在 20-60 字\n");
        sb.append("4. 任务标题要简洁有力（8-16 字），一眼能看出要做什么\n");
        sb.append("5. 为每个任务推荐一个最合适的执行岗位：PROJECT_MANAGER、PRODUCT_MANAGER、FRONTEND_DEV、BACKEND_DEV、QA_TESTER、UI_DESIGNER\n");
        sb.append("6. 这些是顶层大任务（父任务），后续可被 AI 进一步拆解为具体子任务\n\n");
        sb.append("【输出格式】\n");
        sb.append("只返回一个 JSON 数组，不要加任何其他文字：\n");
        sb.append("[{\"title\": \"任务标题\", \"description\": \"该阶段核心工作内容\", \"recommended_role\": \"BACKEND_DEV\"}]\n\n");
        sb.append("示例（项目=电商平台）：\n");
        sb.append("[{\"title\": \"设计账目数据模型\", \"description\": \"设计收入支出、分类、账户和时间字段，建立必要索引\", \"recommended_role\": \"BACKEND_DEV\"}, {\"title\": \"开发账目管理接口\", \"description\": \"实现账目新增、编辑、删除、查询及统计接口\", \"recommended_role\": \"BACKEND_DEV\"}, {\"title\": \"实现记账操作页面\", \"description\": \"开发记账表单、账目列表、筛选和移动端交互\", \"recommended_role\": \"FRONTEND_DEV\"}, {\"title\": \"设计数据统计界面\", \"description\": \"设计收支趋势、分类占比和余额概览的可视化界面\", \"recommended_role\": \"UI_DESIGNER\"}, {\"title\": \"完成联调与验收测试\", \"description\": \"覆盖核心记账流程、边界条件和权限场景，修复上线问题\", \"recommended_role\": \"QA_TESTER\"}]");
        return sb.toString();
    }

    private Task requireActiveTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null || task.getDeletedAt() != null) throw new BusinessException("任务不存在或已移入回收站");
        return task;
    }
}
