package com.smartpm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CommentCreateDTO {
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论不能超过 2000 字")
    private String content;

    @Size(max = 50, message = "单条评论最多提醒 50 人")
    private List<Long> mentionedUserIds = new ArrayList<>();
}
