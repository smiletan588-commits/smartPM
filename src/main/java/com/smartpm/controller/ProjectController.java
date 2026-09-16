package com.smartpm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartpm.common.result.R;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.dto.MilestoneDTO;
import com.smartpm.dto.AIProjectPlanDTO;
import com.smartpm.common.websocket.TaskWebSocketHandler;
import com.smartpm.entity.Project;
import com.smartpm.entity.ProjectMember;
import com.smartpm.entity.ProjectMilestone;
import com.smartpm.entity.User;
import com.smartpm.entity.AiOperationLog;
import com.smartpm.mapper.ProjectMemberMapper;
import com.smartpm.mapper.UserMapper;
import com.smartpm.service.ProjectService;
import com.smartpm.service.MilestoneService;
import com.smartpm.service.AIPlanningService;
import com.smartpm.service.AiOperationLogService;
import com.smartpm.vo.ProjectSummaryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final AIPlanningService aiPlanningService;
    private final TaskWebSocketHandler wsHandler;
    private final MilestoneService milestoneService;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserMapper userMapper;
    private final AiOperationLogService aiLogService;

    @PostMapping("/create")
    public R<Project> create(@RequestParam String name,
                             @RequestParam(required = false) String description) {
        Project project = projectService.create(name, description);
        return R.ok(project);
    }

    @GetMapping("/list")
    public R<List<ProjectSummaryVO>> list() {
        List<Project> projects = projectService.list();
        Long currentUserId = UserHolder.getUserId();
        Map<Long, String> permissions = projectMemberMapper.selectList(
                        new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, currentUserId))
                .stream().collect(Collectors.toMap(ProjectMember::getProjectId,
                        member -> member.getPermission() == null ? "MEMBER" : member.getPermission(),
                        (left, right) -> left));
        return R.ok(projects.stream()
                .map(project -> ProjectSummaryVO.from(project, currentUserId, permissions.get(project.getId())))
                .toList());
    }

    @PutMapping("/update")
    public R<Project> update(@RequestParam Long id,
                             @RequestParam(required = false) String name,
                             @RequestParam(required = false) String description) {
        Project project = projectService.update(id, name, description);
        return R.ok(project);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        wsHandler.broadcast(id, "{\"type\":\"TASK_UPDATED\"}");
        return R.ok();
    }

    /**
     * 获取项目成员列表（含昵称和身份），用于前端角色标签渲染。
     */
    @GetMapping("/{projectId}/members")
    public R<List<Map<String, Object>>> members(@PathVariable Long projectId) {
        projectService.assertProjectAccess(projectId, false);
        Project project = projectService.getByIdForAccess(projectId);
        List<ProjectMember> members = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId));
        if (members.stream().noneMatch(m -> m.getUserId().equals(project.getCreatorId()))) {
            ProjectMember owner = new ProjectMember();
            owner.setProjectId(projectId); owner.setUserId(project.getCreatorId());
            owner.setPermission("PROJECT_ADMIN");
            members.add(0, owner);
        }
        if (members.isEmpty()) return R.ok(List.of());

        List<Long> userIds = members.stream().map(ProjectMember::getUserId).collect(Collectors.toList());
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectMember m : members) {
            User u = userMap.get(m.getUserId());
            if (u == null) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("userId", u.getId());
            item.put("username", u.getUsername());
            item.put("nickname", u.getNickname());
            item.put("identity", m.getIdentity() != null ? m.getIdentity() : u.getIdentity());
            item.put("permission", m.getPermission() != null ? m.getPermission() : "MEMBER");
            item.put("owner", project.getCreatorId().equals(u.getId()));
            item.put("canManage", projectService.canManageMembers(projectId));
            result.add(item);
        }
        return R.ok(result);
    }

    @PostMapping("/{projectId}/members/invite")
    public R<Void> inviteMember(@PathVariable Long projectId,
                                @RequestParam String username,
                                @RequestParam(required = false) String identity,
                                @RequestParam(required = false, defaultValue = "MEMBER") String permission) {
        projectService.inviteMember(projectId, username, identity, permission);
        return R.ok();
    }

    @PutMapping("/{projectId}/members/{userId}")
    public R<Void> updateMember(@PathVariable Long projectId,
                                @PathVariable Long userId,
                                @RequestParam(required = false) String identity,
                                @RequestParam(required = false) String permission) {
        projectService.updateMember(projectId, userId, identity, permission);
        return R.ok();
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    public R<Void> removeMember(@PathVariable Long projectId, @PathVariable Long userId) {
        projectService.removeMember(projectId, userId);
        return R.ok();
    }

    @PostMapping("/{projectId}/transfer-owner")
    public R<Void> transferOwner(@PathVariable Long projectId, @RequestParam Long userId) {
        projectService.transferOwner(projectId, userId);
        return R.ok();
    }

    @GetMapping("/{projectId}/invite-code")
    public R<Map<String, String>> inviteCode(@PathVariable Long projectId) {
        return R.ok(Map.of("inviteCode", projectService.getInviteCode(projectId)));
    }

    @PostMapping("/join")
    public R<Project> join(@RequestParam String inviteCode) {
        return R.ok(projectService.joinByInviteCode(inviteCode));
    }

    @PutMapping("/{projectId}/my-identity")
    public R<Void> updateMyIdentity(@PathVariable Long projectId, @RequestParam String identity) {
        projectService.updateMyProjectIdentity(projectId, identity);
        return R.ok();
    }

    @GetMapping("/{projectId}/milestones")
    public R<List<ProjectMilestone>> milestones(@PathVariable Long projectId) {
        return R.ok(milestoneService.list(projectId));
    }

    @PostMapping("/{projectId}/milestones")
    public R<ProjectMilestone> createMilestone(@PathVariable Long projectId, @Valid @RequestBody MilestoneDTO dto) {
        return R.ok(milestoneService.create(projectId, dto));
    }

    @PutMapping("/{projectId}/milestones")
    public R<ProjectMilestone> updateMilestone(@PathVariable Long projectId, @Valid @RequestBody MilestoneDTO dto) {
        return R.ok(milestoneService.update(projectId, dto));
    }

    @DeleteMapping("/{projectId}/milestones/{milestoneId}")
    public R<Void> deleteMilestone(@PathVariable Long projectId, @PathVariable Long milestoneId) {
        milestoneService.delete(projectId, milestoneId);
        return R.ok();
    }

    @PostMapping("/{projectId}/ai-plan")
    public ResponseEntity<R<AIProjectPlanDTO>> generateAiPlan(@PathVariable Long projectId) {
        AiOperationLog operation = aiLogService.start("PROJECT_PLAN", projectId, null);
        try {
            AIProjectPlanDTO plan = aiPlanningService.generateProjectPlan(projectId);
            aiLogService.succeed(operation, plan.getTasks() == null ? 0 : plan.getTasks().size(), false);
            return ResponseEntity.ok().header("X-AI-Operation-Id", String.valueOf(operation.getId())).body(R.ok(plan));
        } catch (RuntimeException e) {
            aiLogService.fail(operation, e);
            throw e;
        }
    }

    @PostMapping("/{projectId}/ai-plan/apply")
    public R<List<com.smartpm.entity.Task>> applyAiPlan(@PathVariable Long projectId,
                                                        @RequestParam(required = false) Long operationId,
                                                        @RequestBody AIProjectPlanDTO plan) {
        List<com.smartpm.entity.Task> tasks = aiPlanningService.applyProjectPlan(projectId, plan);
        if (operationId != null) aiLogService.markApplied(operationId, 0);
        wsHandler.broadcast(projectId, "{\"type\":\"TASK_UPDATED\"}");
        return R.ok(tasks);
    }

    /**
     * AI 项目周报 — SSE 流式推送。
     * 超时 300 秒，兼容大模型较长响应。
     * 三层异常防护：
     *   1. generateSummary() 同步异常 → catch 后通过 SSE 推送 [ERROR]
     *   2. Flux 异步 error → subscribe error 回调 → SSE 推送 [ERROR]
     *   3. SSE 超时 / 连接异常 → onTimeout / onError 回调
     */
    @GetMapping(value = "/{projectId}/ai-summary", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiSummary(@PathVariable Long projectId) {
        log.info("[AI-Summary] 收到请求: projectId={}", projectId);
        AiOperationLog operation = aiLogService.start("WEEKLY_SUMMARY", projectId, null);

        SseEmitter emitter = new SseEmitter(300_000L);

        try {
            projectService.generateSummary(projectId).subscribe(
                    chunk -> {
                        try {
                            emitter.send(SseEmitter.event().data(chunk));
                        } catch (IOException e) {
                            log.warn("[AI-Summary] SSE 发送失败，客户端可能已断开: {}", e.getMessage());
                            emitter.completeWithError(e);
                        }
                    },
                    error -> {
                        aiLogService.fail(operation, error);
                        log.error("[AI-Summary] 异步流生成异常，具体原因为：", error);
                        try {
                            String errMsg = "[ERROR] " + (error.getMessage() != null
                                    ? error.getMessage() : "未知错误");
                            emitter.send(SseEmitter.event().data(errMsg));
                        } catch (IOException ignored) {
                            // 客户端可能已断开
                        }
                        emitter.completeWithError(error);
                    },
                    () -> {
                        aiLogService.succeed(operation, 0, false);
                        log.info("[AI-Summary] 流式响应完成 projectId={}", projectId);
                        emitter.complete();
                    }
            );
        } catch (Exception e) {
            aiLogService.fail(operation, e);
            // 捕获 generateSummary() 的同步异常（如项目不存在）
            log.error("[AI-Summary] 同步初始化失败，具体原因为：", e);
            try {
                String errMsg = "[ERROR] " + (e.getMessage() != null ? e.getMessage() : "系统异常");
                emitter.send(SseEmitter.event().data(errMsg));
            } catch (IOException ignored) {
            }
            emitter.completeWithError(e);
        }

        emitter.onTimeout(() -> {
            log.warn("[AI-Summary] SSE 超时 projectId={}", projectId);
            emitter.complete();
        });
        emitter.onError(throwable -> {
            log.error("[AI-Summary] SSE 连接异常 projectId={}", projectId, throwable);
        });

        return emitter;
    }
}
