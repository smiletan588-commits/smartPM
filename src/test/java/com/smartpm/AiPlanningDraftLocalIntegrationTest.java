package com.smartpm;

import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.dto.AiPlanningDraftVO;
import com.smartpm.dto.AiPlanningInputDTO;
import com.smartpm.entity.User;
import com.smartpm.service.AIService;
import com.smartpm.service.AiPlanningDraftService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Opt-in local MySQL integration test, kept separate from the user's smartpm database. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SMARTPM_LOCAL_AI_QA", matches = "true")
class AiPlanningDraftLocalIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:3306/smartpm_ai_qa?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> System.getenv("SMARTPM_AI_QA_PASSWORD"));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AiPlanningDraftService drafts;
    @MockBean AIService ai;

    @AfterEach void clearUser() { UserHolder.remove(); }

    @Test
    @Transactional
    void draftSurvivesReloadAndOnlyConfirmedTasksAreCreated() {
        Long projectId = project();
        when(ai.chatJson(anyString())).thenReturn("""
            {"overview":"筹备活动报名","assumptions":[],"tasks":[{"title":"组织志愿者培训","description":"制定培训内容并组织培训","deliverable":"培训签到表和讲义","acceptanceCriteria":"签到表有参训成员签名","fitReason":"保障活动执行","recommendedSkill":"活动策划","priority":"MEDIUM","dependencyIndexes":[]}]}
            """);
        AiPlanningDraftVO generated = drafts.generate(projectId, input("INIT"), null);
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_task WHERE project_id=?", Long.class, projectId));
        AiPlanningDraftVO loaded = drafts.load(projectId, generated.getId());
        assertEquals("组织志愿者培训", loaded.getContent().getTasks().get(0).getTitle());
        assertTrue(loaded.getIssues().isEmpty());
        loaded.getContent().getTasks().get(0).setTitle("完成志愿者培训");
        AiPlanningDraftVO saved = drafts.save(projectId, loaded.getId(), loaded.getVersion(), loaded.getContent());
        assertEquals(2, saved.getVersion());
        assertEquals(1, drafts.apply(projectId, saved.getId(), saved.getVersion()).size());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_task WHERE project_id=?", Long.class, projectId));
        assertNull(jdbc.queryForObject("SELECT assignee_id FROM sys_task WHERE project_id=?", Long.class, projectId));
        assertEquals("活动策划", jdbc.queryForObject("SELECT recommended_skill FROM sys_task WHERE project_id=?", String.class, projectId));
        assertThrows(BusinessException.class, () -> drafts.apply(projectId, saved.getId(), saved.getVersion()));
    }

    @Test
    @Transactional
    void viewerCannotGenerateDraft() {
        Long projectId = project();
        jdbc.update("INSERT INTO sys_user(username,password,system_role,status) VALUES (?,?,?,?)", "ai-draft-viewer-qa", "x", "USER", "ACTIVE");
        Long viewerId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, "ai-draft-viewer-qa");
        jdbc.update("INSERT INTO pm_project_member(project_id,user_id,permission) VALUES (?,?,?)", projectId, viewerId, "VIEWER");
        User viewer = new User(); viewer.setId(viewerId); UserHolder.set(viewer);
        assertThrows(BusinessException.class, () -> drafts.generate(projectId, input("INIT"), null));
    }

    @Test
    @Transactional
    void invalidDraftCannotWriteTasksAndCannotCrossProjects() {
        Long projectId = project();
        when(ai.chatJson(anyString())).thenReturn("""
            {"overview":"筹备活动报名","tasks":[{"title":"招募志愿者","description":"确定招募渠道","deliverable":"招募名单","acceptanceCriteria":"名单经负责人确认","fitReason":"满足活动人力需求","priority":"MEDIUM"}]}
            """);
        AiPlanningDraftVO generated = drafts.generate(projectId, input("INIT"), null);
        jdbc.update("INSERT INTO sys_project(name,description,creator_id) SELECT '另一项目','隔离验证',creator_id FROM sys_project WHERE id=?", projectId);
        Long otherProjectId = jdbc.queryForObject("SELECT id FROM sys_project WHERE name='另一项目' ORDER BY id DESC LIMIT 1", Long.class);
        assertThrows(BusinessException.class, () -> drafts.load(otherProjectId, generated.getId()));
        assertThrows(BusinessException.class, () -> drafts.apply(otherProjectId, generated.getId(), generated.getVersion()));

        generated.getContent().getTasks().get(0).setDeliverable(null);
        AiPlanningDraftVO invalid = drafts.save(projectId, generated.getId(), generated.getVersion(), generated.getContent());
        assertTrue(invalid.getIssues().stream().anyMatch(issue -> issue.code().equals("DELIVERABLE") && issue.blocking()));
        assertThrows(BusinessException.class, () -> drafts.apply(projectId, invalid.getId(), invalid.getVersion()));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_task WHERE project_id=?", Long.class, projectId));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM pm_ai_planning_draft WHERE id=?", String.class, invalid.getId()));
    }

    private Long project() {
        jdbc.update("INSERT INTO sys_user(username,password,system_role,status) VALUES (?,?,?,?)", "ai-draft-owner-qa", "x", "USER", "ACTIVE");
        Long ownerId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, "ai-draft-owner-qa");
        jdbc.update("INSERT INTO sys_project(name,description,creator_id) VALUES (?,?,?)", "志愿活动", "组织社区活动", ownerId);
        Long projectId = jdbc.queryForObject("SELECT id FROM sys_project WHERE creator_id=? AND name=? ORDER BY id DESC LIMIT 1", Long.class, ownerId, "志愿活动");
        User owner = new User(); owner.setId(ownerId); UserHolder.set(owner);
        return projectId;
    }

    private AiPlanningInputDTO input(String mode) {
        AiPlanningInputDTO input = new AiPlanningInputDTO();
        input.setMode(mode); input.setProjectType("GENERAL");
        input.setGoal("组织一场社区志愿活动"); input.setDeliverable("活动、签到和总结"); input.setScope("招募、培训和现场执行");
        input.setWikiIds(List.of());
        return input;
    }
}
