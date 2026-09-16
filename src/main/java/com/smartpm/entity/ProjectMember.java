package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pm_project_member")
public class ProjectMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long userId;
    /** 项目内专业身份：PROJECT_MANAGER / PRODUCT_MANAGER / FRONTEND_DEV / BACKEND_DEV / QA_TESTER / UI_DESIGNER */
    private String identity;
    /** 项目权限：PROJECT_ADMIN / MEMBER / VIEWER */
    private String permission;
    private LocalDateTime joinedAt;
}
