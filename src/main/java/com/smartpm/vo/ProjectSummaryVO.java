package com.smartpm.vo;

import com.smartpm.entity.Project;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectSummaryVO {
    private Long id;
    private String name;
    private String description;
    private String inviteCode;
    private Long creatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean owner;
    private String myPermission;

    public static ProjectSummaryVO from(Project project, Long currentUserId, String permission) {
        ProjectSummaryVO vo = new ProjectSummaryVO();
        vo.setId(project.getId());
        vo.setName(project.getName());
        vo.setDescription(project.getDescription());
        vo.setInviteCode(project.getInviteCode());
        vo.setCreatorId(project.getCreatorId());
        vo.setCreatedAt(project.getCreatedAt());
        vo.setUpdatedAt(project.getUpdatedAt());
        boolean owner = project.getCreatorId().equals(currentUserId);
        vo.setOwner(owner);
        vo.setMyPermission(owner ? "PROJECT_ADMIN" : (permission == null ? "MEMBER" : permission));
        return vo;
    }
}
