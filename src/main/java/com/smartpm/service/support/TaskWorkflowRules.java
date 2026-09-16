package com.smartpm.service.support;

import com.smartpm.common.exception.BusinessException;

import java.util.Objects;

/** 主任务只允许按待办、进行中、已完成的顺序向前流转。 */
public final class TaskWorkflowRules {
    private TaskWorkflowRules() {
    }

    public static void assertTransition(String sourceStatus, String targetStatus) {
        if (Objects.equals(sourceStatus, targetStatus)) return;
        if ("TODO".equals(sourceStatus) && "IN_PROGRESS".equals(targetStatus)) return;
        if ("IN_PROGRESS".equals(sourceStatus) && "DONE".equals(targetStatus)) return;

        if ("TODO".equals(sourceStatus) && "DONE".equals(targetStatus)) {
            throw new BusinessException("待办任务必须先领取并开始，不能越级完成");
        }
        if ("DONE".equals(sourceStatus)) {
            throw new BusinessException("已完成任务不能直接退回；发现 Bug 时请由测试工程师执行打回");
        }
        throw new BusinessException("任务只能按“待办 → 进行中 → 已完成”顺序流转");
    }
}
