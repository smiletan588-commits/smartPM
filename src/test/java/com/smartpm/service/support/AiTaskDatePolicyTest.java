package com.smartpm.service.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiTaskDatePolicyTest {

    private static final LocalDate GENERATED_ON = LocalDate.of(2026, 9, 14);

    @Test
    void defaultsMissingDatesToFifteenDaysAfterGeneration() {
        AiTaskDatePolicy.DateRange result = AiTaskDatePolicy.resolve(null, null, GENERATED_ON);

        assertEquals(GENERATED_ON, result.startDate());
        assertEquals(LocalDate.of(2026, 9, 29), result.dueDate());
    }

    @Test
    void preservesValidAiDates() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate due = LocalDate.of(2026, 10, 8);

        AiTaskDatePolicy.DateRange result = AiTaskDatePolicy.resolve(start, due, GENERATED_ON);

        assertEquals(start, result.startDate());
        assertEquals(due, result.dueDate());
    }

    @Test
    void defaultsMissingOrInvalidDeadlineFromResolvedStart() {
        LocalDate start = LocalDate.of(2026, 10, 1);

        assertEquals(LocalDate.of(2026, 10, 16),
                AiTaskDatePolicy.resolve(start, null, GENERATED_ON).dueDate());
        assertEquals(LocalDate.of(2026, 10, 16),
                AiTaskDatePolicy.resolve(start, start.minusDays(1), GENERATED_ON).dueDate());
    }
}
