package com.smartpm.controller;

import com.smartpm.common.result.R;
import com.smartpm.common.websocket.TaskWebSocketHandler;
import com.smartpm.dto.DragDTO;
import com.smartpm.dto.TaskUpdateDTO;
import com.smartpm.dto.BatchTaskUpdateDTO;
import com.smartpm.dto.AITaskOptimizationVO;
import com.smartpm.dto.CommentCreateDTO;
import com.smartpm.entity.AttachmentDownloadLog;
import com.smartpm.entity.TaskActivity;
import com.smartpm.entity.TaskComment;
import com.smartpm.entity.AiOperationLog;
import com.smartpm.entity.Task;
import com.smartpm.entity.TaskAttachment;
import com.smartpm.mapper.TaskMapper;
import com.smartpm.service.TaskAttachmentService;
import com.smartpm.service.TaskService;
import com.smartpm.service.AIPlanningService;
import com.smartpm.service.CollaborationService;
import com.smartpm.service.AiOperationLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
@Validated
public class TaskController {

    private final TaskService taskService;
    private final AIPlanningService aiPlanningService;
    private final TaskAttachmentService attachmentService;
    private final TaskMapper taskMapper;
    private final TaskWebSocketHandler wsHandler;
    private final CollaborationService collaborationService;
    private final AiOperationLogService aiLogService;

    @PostMapping("/create")
    public R<Task> create(@RequestParam Long projectId,
                          @RequestParam @Size(min = 1, max = 255, message = "任务标题长度应为 1-255 字") String title,
                          @RequestParam(required = false) @Size(max = 5000, message = "任务描述不能超过 5000 字") String description,
                          @RequestParam(required = false) Long assigneeId,
                          @RequestParam(required = false) @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "截止日期格式应为 yyyy-MM-dd") String dueDate,
                          @RequestParam(required = false) @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "开始日期格式应为 yyyy-MM-dd") String startDate,
                          @RequestParam(required = false) String priority,
                          @RequestParam(required = false) String tags,
                          @RequestParam(required = false) String dependencyIds,
                          @RequestParam(required = false) @Min(0) @Max(10000) Integer estimatedHours,
                          @RequestParam(required = false) @Min(0) @Max(10000) Integer actualHours,
                          @RequestParam(required = false) @Size(max = 5000, message = "验收标准不能超过 5000 字") String acceptanceCriteria) {
        Task task = taskService.create(projectId, title, description, assigneeId, dueDate,
                startDate, priority, tags, dependencyIds, estimatedHours, actualHours, acceptanceCriteria);
        wsHandler.broadcast(projectId, "{\"type\":\"TASK_UPDATED\"}");
        return R.ok(task);
    }

    @GetMapping("/list/{projectId}")
    public R<List<Task>> listByProject(@PathVariable Long projectId) {
        List<Task> tasks = taskService.listByProject(projectId);
        return R.ok(tasks);
    }

    @PutMapping("/update")
    public R<Task> update(@Valid @RequestBody TaskUpdateDTO dto) {
        Task task = taskService.update(dto);
        wsHandler.broadcast(task.getProjectId(), "{\"type\":\"TASK_UPDATED\"}");
        return R.ok(task);
    }

    @PutMapping("/batch")
    public R<List<Task>> batchUpdate(@Valid @RequestBody BatchTaskUpdateDTO dto) {
        List<Task> tasks = taskService.batchUpdate(dto);
        if (!tasks.isEmpty()) wsHandler.broadcast(tasks.get(0).getProjectId(), "{\"type\":\"TASK_UPDATED\"}");
        return R.ok(tasks);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long projectId = getProjectId(id);
        taskService.delete(id);
        wsHandler.broadcast(projectId, "{\"type\":\"TASK_UPDATED\"}");
        return R.ok();
    }

    @PutMapping("/drag")
    public R<Void> drag(@RequestBody DragDTO dto) {
        Long projectId = getProjectId(dto.getTaskId());
        taskService.drag(dto);
        wsHandler.broadcast(projectId, "{\"type\":\"TASK_UPDATED\"}");
        return R.ok();
    }

    @PostMapping("/{taskId}/ai-decompose")
    public ResponseEntity<R<List<Task>>> aiDecompose(@PathVariable Long taskId) {
        Long projectId = getProjectId(taskId);
        AiOperationLog operation = aiLogService.start("TASK_DECOMPOSE", projectId, taskId);
        try {
            List<Task> subtasks = taskService.decomposeTask(taskId);
            aiLogService.succeed(operation, subtasks.size(), true);
            wsHandler.broadcast(projectId, "{\"type\":\"TASK_UPDATED\"}");
            return ResponseEntity.ok().header("X-AI-Operation-Id", String.valueOf(operation.getId())).body(R.ok(subtasks));
        } catch (RuntimeException e) {
            aiLogService.fail(operation, e);
            throw e;
        }
    }

    @PostMapping("/{taskId}/ai-optimize")
    public ResponseEntity<R<AITaskOptimizationVO>> aiOptimize(@PathVariable Long taskId) {
        Long projectId = getProjectId(taskId);
        AiOperationLog operation = aiLogService.start("TASK_OPTIMIZE", projectId, taskId);
        try {
            AITaskOptimizationVO result = aiPlanningService.optimizeTask(taskId);
            aiLogService.succeed(operation, 0, false);
            return ResponseEntity.ok().header("X-AI-Operation-Id", String.valueOf(operation.getId())).body(R.ok(result));
        } catch (RuntimeException e) {
            aiLogService.fail(operation, e);
            throw e;
        }
    }

    @GetMapping("/{taskId}/subtasks")
    public R<List<Task>> listSubtasks(@PathVariable Long taskId) {
        return R.ok(taskService.listSubtasks(taskId));
    }

    @PutMapping("/{taskId}/toggle-subtask")
    public R<Task> toggleSubtask(@PathVariable Long taskId) {
        Task task = taskService.toggleSubtask(taskId);
        wsHandler.broadcast(task.getProjectId(), "{\"type\":\"TASK_UPDATED\"}");
        return R.ok(task);
    }

    @PostMapping("/{projectId}/ai-init-tasks")
    public ResponseEntity<R<List<Task>>> aiInitTasks(@PathVariable Long projectId) {
        AiOperationLog operation = aiLogService.start("PROJECT_INIT", projectId, null);
        try {
            List<Task> tasks = taskService.initTasks(projectId);
            aiLogService.succeed(operation, tasks.size(), true);
            wsHandler.broadcast(projectId, "{\"type\":\"TASK_UPDATED\"}");
            return ResponseEntity.ok().header("X-AI-Operation-Id", String.valueOf(operation.getId())).body(R.ok(tasks));
        } catch (RuntimeException e) {
            aiLogService.fail(operation, e);
            throw e;
        }
    }

    @GetMapping("/{taskId}/attachments")
    public R<List<TaskAttachment>> listAttachments(@PathVariable Long taskId) {
        return R.ok(attachmentService.list(taskId));
    }

    @PostMapping(value = "/{taskId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<TaskAttachment> uploadAttachment(@PathVariable Long taskId, @RequestParam("file") MultipartFile file) {
        TaskAttachment attachment = attachmentService.upload(taskId, file);
        wsHandler.broadcast(attachment.getProjectId(), "{\"type\":\"TASK_UPDATED\"}");
        return R.ok(attachment);
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<FileSystemResource> downloadAttachment(@PathVariable Long attachmentId,
                                                                  @RequestParam(defaultValue = "false") boolean inline) {
        TaskAttachment attachment = attachmentService.get(attachmentId);
        Path path = attachmentService.download(attachmentId);
        MediaType mediaType;
        try {
            mediaType = attachment.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(attachment.getContentType());
        } catch (IllegalArgumentException ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(path.toFile().length())
                .header(HttpHeaders.CONTENT_DISPOSITION, (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(attachment.getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(path));
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public R<Void> deleteAttachment(@PathVariable Long attachmentId) {
        Long projectId = attachmentService.get(attachmentId).getProjectId();
        attachmentService.delete(attachmentId);
        wsHandler.broadcast(projectId, "{\"type\":\"TASK_UPDATED\"}");
        return R.ok();
    }

    @GetMapping("/attachments/{attachmentId}/download-logs")
    public R<List<AttachmentDownloadLog>> attachmentDownloadLogs(@PathVariable Long attachmentId) {
        return R.ok(attachmentService.listDownloadLogs(attachmentId));
    }

    @GetMapping("/{taskId}/comments")
    public R<List<TaskComment>> comments(@PathVariable Long taskId) {
        return R.ok(collaborationService.listComments(taskId));
    }

    @PostMapping("/{taskId}/comments")
    public R<TaskComment> addComment(@PathVariable Long taskId, @Valid @RequestBody CommentCreateDTO dto) {
        TaskComment comment = collaborationService.addComment(taskId, dto);
        wsHandler.broadcast(comment.getProjectId(), "{\"type\":\"COMMENT_UPDATED\",\"taskId\":" + taskId + "}");
        return R.ok(comment);
    }

    @DeleteMapping("/{taskId}/comments/{commentId}")
    public R<Void> deleteComment(@PathVariable Long taskId, @PathVariable Long commentId) {
        Long projectId = getProjectId(taskId);
        collaborationService.deleteComment(taskId, commentId);
        wsHandler.broadcast(projectId, "{\"type\":\"COMMENT_UPDATED\",\"taskId\":" + taskId + "}");
        return R.ok();
    }

    @GetMapping("/{taskId}/activities")
    public R<List<TaskActivity>> activities(@PathVariable Long taskId) {
        return R.ok(collaborationService.listActivities(taskId));
    }

    /** 根据 taskId 获取所属 projectId */
    private Long getProjectId(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        return task != null ? task.getProjectId() : null;
    }
}
