package com.smartpm;

import com.smartpm.common.utils.UserHolder;
import com.smartpm.dto.AiPlanningDraftVO;
import com.smartpm.dto.AiPlanningInputDTO;
import com.smartpm.entity.User;
import com.smartpm.service.AiPlanningDraftService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in live AI smoke test against the isolated local QA database. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SMARTPM_LIVE_AI_QA", matches = "true")
class AiPlanningLiveLocalIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:3306/smartpm_ai_qa?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> System.getenv("SMARTPM_AI_QA_PASSWORD"));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AiPlanningDraftService drafts;

    @AfterEach void clearUser() { UserHolder.remove(); }

    @Test
    @Transactional
    void realJsonModeProducesReviewableDraftWithoutCreatingTasks() {
        jdbc.update("INSERT INTO sys_user(username,password,system_role,status) VALUES (?,?,?,?)", "ai-live-qa", "x", "USER", "ACTIVE");
        Long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, "ai-live-qa");
        jdbc.update("INSERT INTO sys_project(name,description,creator_id) VALUES (?,?,?)", "社区志愿活动演示", "筹备一场小型公益活动", userId);
        Long projectId = jdbc.queryForObject("SELECT id FROM sys_project WHERE creator_id=? ORDER BY id DESC LIMIT 1", Long.class, userId);
        User user = new User(); user.setId(userId); UserHolder.set(user);

        AiPlanningInputDTO input = new AiPlanningInputDTO();
        input.setMode("PLAN"); input.setProjectType("GENERAL");
        input.setGoal("完成一场20人的社区清洁志愿活动");
        input.setDeliverable("活动方案、签到表和现场总结");
        input.setScope("只规划方案确认、参与者通知和当天执行，不包括软件开发");

        AiPlanningDraftVO draft = drafts.generate(projectId, input, null);
        assertNotNull(draft.getId());
        assertNotNull(draft.getContent());
        assertNotNull(draft.getContent().getTasks());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_task WHERE project_id=?", Long.class, projectId));
    }
}
