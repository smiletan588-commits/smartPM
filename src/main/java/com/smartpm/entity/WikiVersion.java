package com.smartpm.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pm_wiki_version")
public class WikiVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long wikiId;
    private Long projectId;
    private Integer versionNo;
    private String title;
    private String content;
    private Long editorId;
    private LocalDateTime createdAt;
    @TableField(exist = false)
    private String editorName;
}
