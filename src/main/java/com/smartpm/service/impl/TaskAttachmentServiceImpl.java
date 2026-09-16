package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.entity.AttachmentDownloadLog;
import com.smartpm.entity.Task;
import com.smartpm.entity.TaskAttachment;
import com.smartpm.mapper.AttachmentDownloadLogMapper;
import com.smartpm.mapper.TaskAttachmentMapper;
import com.smartpm.mapper.TaskMapper;
import com.smartpm.service.ProjectService;
import com.smartpm.service.TaskAttachmentService;
import com.smartpm.service.CollaborationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskAttachmentServiceImpl implements TaskAttachmentService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp", "pdf", "zip", "rar", "7z");

    private final TaskAttachmentMapper attachmentMapper;
    private final AttachmentDownloadLogMapper downloadLogMapper;
    private final TaskMapper taskMapper;
    private final ProjectService projectService;
    private final CollaborationService collaborationService;

    @Value("${smartpm.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public List<TaskAttachment> list(Long taskId) {
        Task task = getTask(taskId);
        projectService.assertProjectAccess(task.getProjectId(), false);
        return attachmentMapper.selectList(new LambdaQueryWrapper<TaskAttachment>()
                .eq(TaskAttachment::getTaskId, taskId)
                .isNull(TaskAttachment::getDeletedAt)
                .orderByDesc(TaskAttachment::getCreatedAt));
    }

    @Override
    public TaskAttachment upload(Long taskId, MultipartFile file) {
        Task task = getTask(taskId);
        projectService.assertProjectAccess(task.getProjectId(), true);
        if (file == null || file.isEmpty()) throw new BusinessException("请选择要上传的附件");
        if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException("附件大小不能超过 20MB");
        String originalName = file.getOriginalFilename() == null ? "attachment" : Path.of(file.getOriginalFilename()).getFileName().toString();
        String extension = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("仅支持图片、PDF 和压缩包格式");
        }
        String storedName = UUID.randomUUID() + "." + extension;
        Path taskDirectory = Path.of(uploadDir, "tasks", String.valueOf(taskId)).toAbsolutePath().normalize();
        Path target = taskDirectory.resolve(storedName).normalize();
        if (!target.startsWith(taskDirectory)) throw new BusinessException("附件路径无效");
        try {
            Files.createDirectories(taskDirectory);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("附件保存失败，请重试");
        }
        TaskAttachment attachment = new TaskAttachment();
        attachment.setTaskId(taskId);
        attachment.setProjectId(task.getProjectId());
        attachment.setOriginalName(originalName);
        attachment.setStoredName(storedName);
        attachment.setContentType(file.getContentType());
        attachment.setSize(file.getSize());
        attachment.setUploaderId(UserHolder.getUserId());
        attachment.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(attachment);
        collaborationService.record(task.getProjectId(), taskId, "ATTACHMENT_UPLOADED",
                "上传了附件 " + originalName, null, java.util.Map.of("attachmentId", attachment.getId()));
        return attachment;
    }

    @Override
    public Path download(Long attachmentId) {
        TaskAttachment attachment = get(attachmentId);
        projectService.assertProjectAccess(attachment.getProjectId(), false);
        Path path = Path.of(uploadDir, "tasks", String.valueOf(attachment.getTaskId()), attachment.getStoredName())
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new BusinessException("附件文件不存在");
        AttachmentDownloadLog log = new AttachmentDownloadLog();
        log.setAttachmentId(attachmentId);
        log.setTaskId(attachment.getTaskId());
        log.setProjectId(attachment.getProjectId());
        log.setDownloaderId(UserHolder.getUserId());
        log.setDownloadedAt(LocalDateTime.now());
        downloadLogMapper.insert(log);
        return path;
    }

    @Override
    public void delete(Long attachmentId) {
        TaskAttachment attachment = get(attachmentId);
        projectService.assertProjectAccess(attachment.getProjectId(), true);
        attachment.setDeletedAt(LocalDateTime.now());
        attachment.setDeletedBy(UserHolder.getUserId());
        attachmentMapper.updateById(attachment);
        collaborationService.record(attachment.getProjectId(), attachment.getTaskId(), "ATTACHMENT_DELETED",
                "删除了附件 " + attachment.getOriginalName(), java.util.Map.of("attachmentId", attachmentId), null);
    }

    @Override
    public List<AttachmentDownloadLog> listDownloadLogs(Long attachmentId) {
        TaskAttachment attachment = get(attachmentId);
        if (!projectService.canManageMembers(attachment.getProjectId())) {
            throw new BusinessException("仅项目管理员可查看附件下载记录");
        }
        return downloadLogMapper.selectList(new LambdaQueryWrapper<AttachmentDownloadLog>()
                .eq(AttachmentDownloadLog::getAttachmentId, attachmentId)
                .orderByDesc(AttachmentDownloadLog::getDownloadedAt));
    }

    @Override
    public TaskAttachment get(Long attachmentId) {
        TaskAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || attachment.getDeletedAt() != null) throw new BusinessException("附件不存在或已移入回收站");
        return attachment;
    }

    private Task getTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || task.getDeletedAt() != null) throw new BusinessException("任务不存在或已移入回收站");
        return task;
    }

    private String extensionOf(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
