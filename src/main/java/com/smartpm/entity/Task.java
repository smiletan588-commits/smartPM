package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父任务ID — NULL=主任务，非NULL=子任务 */
    private Long parentId;

    private Long projectId;

    private String title;

    private String description;

    private String status;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long assigneeId;

    /** AI 推荐的专业身份角色 */
    private String recommendedRole;

    /** 不属于系统固定岗位的能力建议，不参与权限或自动指派。 */
    private String recommendedSkill;

    /** 任务优先级：HIGH / MEDIUM / LOW */
    private String priority;

    /** 任务标签，使用英文代码逗号分隔保存 */
    private String tags;

    private Long creatorId;

    private LocalDate dueDate;

    private LocalDate startDate;

    /** 预计工时（小时） */
    private Integer estimatedHours;

    /** 实际工时（小时） */
    private Integer actualHours;

    /** 前置依赖任务 ID，使用逗号分隔保存 */
    private String dependencyIds;

    /** 可验证的任务完成条件 */
    private String acceptanceCriteria;

    /** 是否需要独立验收流程。 */
    private Boolean reviewRequired;

    /** NOT_REQUIRED / NOT_READY / PENDING / IN_REVIEW / PASSED / REJECTED */
    private String acceptanceStatus;

    private LocalDateTime acceptanceSubmittedAt;

    /** 查询时计算，不落库：是否被未完成前置任务阻塞 */
    @TableField(exist = false)
    private Boolean blocked;

    /** 查询时计算，不落库：阻塞当前任务的前置任务标题 */
    @TableField(exist = false)
    private java.util.List<String> blockedByTaskTitles;

    private Integer orderIndex;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 最近一次进入 DONE 状态的时间，重新打开任务时清空。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime completedAt;

    /** 是否由 AI 直接生成，用于效果统计。 */
    private Boolean aiGenerated;

    /** 非空表示已移入回收站。 */
    private LocalDateTime deletedAt;

    private Long deletedBy;
}
