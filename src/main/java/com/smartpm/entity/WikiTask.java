package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pm_wiki_task")
public class WikiTask {
    @TableId
    private Long wikiId;
    private Long taskId;
    private Long projectId;
    private LocalDateTime createdAt;
}
