package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("pm_task_dependency")
public class TaskDependency {
    @TableId
    private Long taskId;
    private Long prerequisiteTaskId;
}
