package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String email;

    private LocalDateTime emailVerifiedAt;

    /** 专业身份：PROJECT_MANAGER / PRODUCT_MANAGER / FRONTEND_DEV / BACKEND_DEV / QA_TESTER / UI_DESIGNER */
    private String identity;

    /** 系统权限：ADMIN / USER。与项目内的成员权限独立。 */
    private String systemRole;

    /** 账号状态：ACTIVE / DISABLED。 */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
