package com.smartpm.service;

import com.smartpm.dto.CommentCreateDTO;
import com.smartpm.entity.TaskActivity;
import com.smartpm.entity.TaskComment;

import java.util.List;

public interface CollaborationService {
    List<TaskComment> listComments(Long taskId);
    TaskComment addComment(Long taskId, CommentCreateDTO dto);
    void deleteComment(Long taskId, Long commentId);
    List<TaskActivity> listActivities(Long taskId);
    void record(Long projectId, Long taskId, String actionType, String summary, Object before, Object after);
}
