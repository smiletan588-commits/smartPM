package com.smartpm.service.support;

import com.smartpm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskWorkflowRulesTest {

    @Test
    void allowsOnlyForwardAdjacentTransitionsAndSameColumnReordering() {
        assertDoesNotThrow(() -> TaskWorkflowRules.assertTransition("TODO", "TODO"));
        assertDoesNotThrow(() -> TaskWorkflowRules.assertTransition("TODO", "IN_PROGRESS"));
        assertDoesNotThrow(() -> TaskWorkflowRules.assertTransition("IN_PROGRESS", "DONE"));
        assertDoesNotThrow(() -> TaskWorkflowRules.assertTransition("DONE", "DONE"));
    }

    @Test
    void rejectsSkippingAndBackwardTransitionsWithActionableMessages() {
        BusinessException skipped = assertThrows(BusinessException.class,
                () -> TaskWorkflowRules.assertTransition("TODO", "DONE"));
        assertEquals("待办任务必须先领取并开始，不能越级完成", skipped.getMessage());

        BusinessException reopened = assertThrows(BusinessException.class,
                () -> TaskWorkflowRules.assertTransition("DONE", "IN_PROGRESS"));
        assertEquals("已完成任务不能直接退回；发现 Bug 时请由测试工程师执行打回", reopened.getMessage());

        BusinessException reversed = assertThrows(BusinessException.class,
                () -> TaskWorkflowRules.assertTransition("IN_PROGRESS", "TODO"));
        assertEquals("任务只能按“待办 → 进行中 → 已完成”顺序流转", reversed.getMessage());
    }
}
