package com.smartpm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpm.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class GraduationFeatureIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("smartpm_test")
            .withUsername("smartpm")
            .withPassword("smartpm_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.baseline-on-migrate", () -> false);
        registry.add("smartpm.jwt-secret", () -> "smartpm-integration-test-secret-with-more-than-32-characters");
        registry.add("smartpm.initial-admin-password", () -> "TestAdmin@2026");
        registry.add("management.health.redis.enabled", () -> false);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @LocalServerPort
    int serverPort;

    @Autowired
    NotificationServiceImpl notificationService;

    @Test
    void collaborationNotificationRiskPermissionAndCompletionFlow() throws Exception {
        long suffix = System.nanoTime();
        String ownerName = "owner" + suffix;
        String viewerName = "viewer" + suffix;
        String outsiderName = "outsider" + suffix;
        register(ownerName, "OwnerPass@2026", "项目负责人");
        long viewerId = register(viewerName, "ViewerPass@2026", "观察员");
        register(outsiderName, "OutsiderPass@2026", "外部成员");

        Session owner = login(ownerName, "OwnerPass@2026");
        long projectId = dataId(mvc.perform(post("/api/project/create")
                        .header("Authorization", owner.authorization())
                        .param("name", "毕业设计集成测试")
                        .param("description", "验证协作、通知与风险闭环"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(post("/api/project/{projectId}/members/invite", projectId)
                        .header("Authorization", owner.authorization())
                        .param("username", viewerName)
                        .param("identity", "QA_TESTER")
                        .param("permission", "VIEWER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));

        Session viewer = login(viewerName, "ViewerPass@2026");

        long prerequisiteId = dataId(mvc.perform(post("/api/task/create")
                        .header("Authorization", owner.authorization())
                        .param("projectId", String.valueOf(projectId))
                        .param("title", "完成数据库设计")
                        .param("assigneeId", String.valueOf(owner.userId()))
                        .param("dueDate", LocalDate.now().plusDays(1).toString())
                        .param("estimatedHours", "12"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.aiGenerated").value(false))
                .andReturn().getResponse().getContentAsString());

        long blockedTaskId = dataId(mvc.perform(post("/api/task/create")
                        .header("Authorization", owner.authorization())
                        .param("projectId", String.valueOf(projectId))
                        .param("title", "联调风险中心")
                        .param("assigneeId", String.valueOf(owner.userId()))
                        .param("dependencyIds", String.valueOf(prerequisiteId))
                        .param("estimatedHours", "16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dependencyIds").value(String.valueOf(prerequisiteId)))
                .andReturn().getResponse().getContentAsString());

        ConnectedSocket ownerProjectSocket = connect("/ws/project/" + projectId + "?token=" + owner.token());
        ConnectedSocket viewerProjectSocket = connect("/ws/project/" + projectId + "?token=" + viewer.token());
        ConnectedSocket viewerNotificationSocket = connect("/ws/notifications?token=" + viewer.token());
        try {
            mvc.perform(post("/api/task/{taskId}/comments", blockedTaskId)
                            .header("Authorization", owner.authorization())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"请验证阻塞逻辑\",\"mentionedUserIds\":[" + viewerId + "]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.authorName").value("项目负责人"));

            assertTrue(awaitMessage(ownerProjectSocket, "COMMENT_UPDATED"), "负责人浏览器应实时收到评论事件");
            assertTrue(awaitMessage(viewerProjectSocket, "COMMENT_UPDATED"), "成员浏览器应实时收到评论事件");
            assertTrue(awaitMessage(viewerNotificationSocket, "NOTIFICATION_UPDATED"), "被提醒成员应实时收到通知事件");

            mvc.perform(put("/api/task/drag")
                            .header("Authorization", owner.authorization())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"taskId\":" + prerequisiteId + ",\"targetStatus\":\"IN_PROGRESS\",\"targetOrderIndex\":0}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
            assertTrue(awaitMessage(ownerProjectSocket, "TASK_UPDATED"), "负责人浏览器应实时收到拖拽事件");
            assertTrue(awaitMessage(viewerProjectSocket, "TASK_UPDATED"), "成员浏览器应实时收到拖拽事件");
        } finally {
            ownerProjectSocket.session().close();
            viewerProjectSocket.session().close();
            viewerNotificationSocket.session().close();
        }

        mvc.perform(get("/api/task/{taskId}/activities", blockedTaskId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(2)));

        mvc.perform(get("/api/project/{projectId}/risks", projectId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediumCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.blockedEdges.length()").value(1))
                .andExpect(jsonPath("$.data.risks[0].taskId").value(blockedTaskId))
                .andExpect(jsonPath("$.data.risks[0].score").value(greaterThanOrEqualTo(30)));

        Session outsider = login(outsiderName, "OutsiderPass@2026");
        mvc.perform(get("/api/project/{projectId}/risks", projectId)
                        .header("Authorization", outsider.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("您不是该项目成员"));

        notificationService.createDeadlineAndBlockedNotifications();
        long scheduledCount = unreadCount(owner);
        notificationService.createDeadlineAndBlockedNotifications();
        assertEquals(scheduledCount, unreadCount(owner), "相同业务日内的到期与阻塞通知必须去重");

        mvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(greaterThanOrEqualTo(2)));

        mvc.perform(get("/api/task/{taskId}/comments", blockedTaskId)
                        .header("Authorization", viewer.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(post("/api/task/{taskId}/comments", blockedTaskId)
                        .header("Authorization", viewer.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"只读成员不应能发送\",\"mentionedUserIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("只读成员不能修改项目内容"));

        mvc.perform(put("/api/task/update")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + prerequisiteId + ",\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        mvc.perform(put("/api/notifications/read-all").header("Authorization", owner.authorization()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(0));

        mvc.perform(get("/api/analytics/ai-overview").header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.successRate").value(0.0));
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.info.title").value("SmartPM API"));
    }

    @Test
    void scheduleBaselineSimulationAndPermissionFlow() throws Exception {
        long suffix = System.nanoTime();
        String ownerName = "scheduleOwner" + suffix;
        String viewerName = "scheduleViewer" + suffix;
        String memberName = "scheduleMember" + suffix;
        String adminName = "scheduleAdmin" + suffix;
        register(ownerName, "OwnerPass@2026", "排期负责人");
        register(viewerName, "ViewerPass@2026", "排期观察员");
        register(memberName, "MemberPass@2026", "排期成员");
        register(adminName, "AdminPass@2026", "排期管理员");
        Session owner = login(ownerName, "OwnerPass@2026");
        Session viewer = login(viewerName, "ViewerPass@2026");
        Session member = login(memberName, "MemberPass@2026");
        Session projectAdmin = login(adminName, "AdminPass@2026");

        long projectId = dataId(mvc.perform(post("/api/project/create")
                        .header("Authorization", owner.authorization())
                        .param("name", "CPM 集成测试"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/project/{projectId}/members/invite", projectId)
                        .header("Authorization", owner.authorization())
                        .param("username", viewerName).param("identity", "QA_TESTER").param("permission", "VIEWER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/api/project/{projectId}/members/invite", projectId)
                        .header("Authorization", owner.authorization())
                        .param("username", memberName).param("identity", "BACKEND_DEV").param("permission", "MEMBER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/api/project/{projectId}/members/invite", projectId)
                        .header("Authorization", owner.authorization())
                        .param("username", adminName).param("identity", "PROJECT_MANAGER").param("permission", "PROJECT_ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));

        LocalDate start = LocalDate.now().plusDays(10);
        long firstTaskId = dataId(mvc.perform(post("/api/task/create")
                        .header("Authorization", owner.authorization())
                        .param("projectId", String.valueOf(projectId)).param("title", "关键设计")
                        .param("startDate", start.toString()).param("dueDate", start.toString())
                        .param("estimatedHours", "8"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long secondTaskId = dataId(mvc.perform(post("/api/task/create")
                        .header("Authorization", owner.authorization())
                        .param("projectId", String.valueOf(projectId)).param("title", "关键开发")
                        .param("dependencyIds", String.valueOf(firstTaskId)).param("estimatedHours", "16"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        mvc.perform(get("/api/project/{projectId}/schedule", projectId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.durationDays").value(3))
                .andExpect(jsonPath("$.data.criticalTaskCount").value(2))
                .andExpect(jsonPath("$.data.inferredTaskCount").value(1))
                .andExpect(jsonPath("$.data.criticalPathTaskIds[0]").value(firstTaskId));

        String baselineResponse = mvc.perform(post("/api/project/{projectId}/schedule/baselines", projectId)
                        .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"初始计划\",\"description\":\"答辩演示基线\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.taskCount").value(2))
                .andReturn().getResponse().getContentAsString();
        long baselineId = dataId(baselineResponse);

        long memberBaselineId = dataId(mvc.perform(post("/api/project/{projectId}/schedule/baselines", projectId)
                        .header("Authorization", member.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"成员评审计划\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(delete("/api/project/{projectId}/schedule/baselines/{baselineId}", projectId, memberBaselineId)
                        .header("Authorization", projectAdmin.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));

        mvc.perform(post("/api/project/{projectId}/schedule/baselines", projectId)
                        .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"初始计划\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("当前项目已存在同名基线"));

        mvc.perform(get("/api/project/{projectId}/schedule/baselines/{baselineId}", projectId, baselineId)
                        .header("Authorization", viewer.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2));
        mvc.perform(post("/api/project/{projectId}/schedule/baselines", projectId)
                        .header("Authorization", viewer.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"只读基线\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("只读成员不能修改项目内容"));

        String simulationBody = "{\"baselineId\":" + baselineId + ",\"taskId\":" + firstTaskId
                + ",\"startDate\":\"" + start.plusDays(5) + "\",\"durationDays\":1}";
        mvc.perform(post("/api/project/{projectId}/schedule/simulate", projectId)
                        .header("Authorization", viewer.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content(simulationBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.projectFinishDeltaDays").value(5))
                .andExpect(jsonPath("$.data.changedTasks.length()").value(2))
                .andExpect(jsonPath("$.data.riskBefore.lowCount").value(1))
                .andExpect(jsonPath("$.data.riskBefore.mediumCount").value(1))
                .andExpect(jsonPath("$.data.riskAfter.mediumCount").value(1))
                .andExpect(jsonPath("$.data.riskAfter.highCount").value(1));

        mvc.perform(post("/api/project/{projectId}/schedule/simulate", projectId)
                        .header("Authorization", viewer.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + firstTaskId + ",\"durationDays\":3651}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(post("/api/project/{projectId}/schedule/simulate", projectId)
                        .header("Authorization", viewer.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + firstTaskId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("请至少调整模拟开始日期或工期"));

        mvc.perform(get("/api/task/list/{projectId}", projectId).header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + firstTaskId + ")].startDate").value(start.toString()))
                .andExpect(jsonPath("$.data[?(@.id == " + firstTaskId + ")].dueDate").value(start.toString()))
                .andExpect(jsonPath("$.data[?(@.id == " + secondTaskId + ")].dependencyIds").value(String.valueOf(firstTaskId)));
        mvc.perform(get("/api/project/{projectId}/schedule/baselines/{baselineId}", projectId, baselineId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.taskId == " + firstTaskId + ")].plannedStartDate").value(start.toString()));

        mvc.perform(get("/api/project/{projectId}/risks", projectId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.baselineId").value(baselineId));
        mvc.perform(get("/api/project/{projectId}/risks", projectId)
                        .param("baselineId", String.valueOf(baselineId)).header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.baselineId").value(baselineId));

        long otherProjectId = dataId(mvc.perform(post("/api/project/create")
                        .header("Authorization", owner.authorization()).param("name", "其他项目"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long otherTaskId = dataId(mvc.perform(post("/api/task/create")
                        .header("Authorization", owner.authorization())
                        .param("projectId", String.valueOf(otherProjectId)).param("title", "其他项目任务"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/project/{projectId}/schedule", otherProjectId)
                        .param("baselineId", String.valueOf(baselineId)).header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("计划基线不存在或不属于当前项目"));
        mvc.perform(post("/api/project/{projectId}/schedule/simulate", otherProjectId)
                        .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baselineId\":" + baselineId + ",\"taskId\":" + otherTaskId + ",\"durationDays\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("计划基线不存在或不属于当前项目"));
        mvc.perform(post("/api/project/{projectId}/schedule/simulate", projectId)
                        .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + otherTaskId + ",\"durationDays\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("模拟任务不存在或不属于当前项目"));

        mvc.perform(put("/api/task/update").header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + firstTaskId + ",\"status\":\"DONE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("待办任务必须先领取并开始，不能越级完成"));
        mvc.perform(put("/api/task/drag").header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + firstTaskId + ",\"targetStatus\":\"DONE\",\"targetOrderIndex\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("待办任务必须先领取并开始，不能越级完成"));
        mvc.perform(put("/api/task/update").header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + firstTaskId + ",\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(put("/api/task/update").header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + firstTaskId + ",\"status\":\"TODO\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("任务只能按“待办 → 进行中 → 已完成”顺序流转"));
        mvc.perform(put("/api/task/update").header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + firstTaskId + ",\"status\":\"DONE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(put("/api/task/update").header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + secondTaskId + ",\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(put("/api/task/update").header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + secondTaskId + ",\"status\":\"DONE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/api/project/{projectId}/schedule/simulate", projectId)
                        .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + secondTaskId + ",\"durationDays\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("已完成任务不能参与延期模拟"));
        mvc.perform(delete("/api/task/{taskId}", secondTaskId).header("Authorization", owner.authorization()))
                .andExpect(status().isOk());
        long addedTaskId = dataId(mvc.perform(post("/api/task/create")
                        .header("Authorization", owner.authorization())
                        .param("projectId", String.valueOf(projectId)).param("title", "基线后新增任务"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/project/{projectId}/schedule/baselines/{baselineId}", projectId, baselineId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2));
        mvc.perform(get("/api/project/{projectId}/schedule", projectId)
                        .param("baselineId", String.valueOf(baselineId)).header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks[?(@.taskId == " + secondTaskId + ")].changeType").value("REMOVED"))
                .andExpect(jsonPath("$.data.tasks[?(@.taskId == " + addedTaskId + ")].changeType").value("ADDED"));

        mvc.perform(delete("/api/project/{projectId}/schedule/baselines/{baselineId}", projectId, baselineId)
                        .header("Authorization", viewer.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("只有项目管理员可以删除计划基线"));
        mvc.perform(delete("/api/project/{projectId}/schedule/baselines/{baselineId}", projectId, baselineId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/api/project/{projectId}/schedule/baselines", projectId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void workplaceWorkspaceRiskWikiTemplateRecurrenceCsvAndEmailFlow() throws Exception {
        long suffix = System.nanoTime();
        String ownerName = "workOwner" + suffix;
        String viewerName = "workViewer" + suffix;
        register(ownerName, "OwnerPass@2026", "工作台负责人");
        long viewerId = register(viewerName, "ViewerPass@2026", "只读观察员");
        Session owner = login(ownerName, "OwnerPass@2026");
        Session viewer = login(viewerName, "ViewerPass@2026");

        long projectId = dataId(mvc.perform(post("/api/project/create").header("Authorization", owner.authorization())
                        .param("name", "职场闭环项目").param("description", "workspace-risk-wiki-template"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/project/{projectId}/members/invite", projectId).header("Authorization", owner.authorization())
                        .param("username", viewerName).param("identity", "QA_TESTER").param("permission", "VIEWER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        long taskId = dataId(mvc.perform(post("/api/task/create").header("Authorization", owner.authorization())
                        .param("projectId", String.valueOf(projectId)).param("title", "今天完成职场闭环")
                        .param("assigneeId", String.valueOf(owner.userId())).param("dueDate", LocalDate.now().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        mvc.perform(get("/api/workspace/overview").header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.todayCount").value(1))
                .andExpect(jsonPath("$.data.tasks[0].projectId").value(projectId));
        mvc.perform(get("/api/search").header("Authorization", viewer.authorization()).param("q", "职场闭环"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.projects[0].id").value(projectId));
        mvc.perform(put("/api/task/batch").header("Authorization", viewer.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskIds\":[" + taskId + "],\"priority\":\"HIGH\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500));
        mvc.perform(put("/api/task/batch").header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskIds\":[" + taskId + "],\"priority\":\"HIGH\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].priority").value("HIGH"));

        mvc.perform(put("/api/project/{projectId}/risks/{taskId}/action", projectId, taskId)
                        .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"ownerUserId\":" + owner.userId()
                                + ",\"responsePlan\":\"当天完成并复核\",\"dueDate\":\"" + LocalDate.now().plusDays(1) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        mvc.perform(get("/api/project/{projectId}/risks/{taskId}/events", projectId, taskId)
                        .header("Authorization", viewer.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));

        long wikiId = dataId(mvc.perform(post("/api/wiki/create").header("Authorization", owner.authorization())
                        .param("projectId", String.valueOf(projectId)).param("title", "验收手册").param("content", "第一版"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(put("/api/wiki/update").header("Authorization", owner.authorization())
                        .param("id", String.valueOf(wikiId)).param("content", "第二版内容"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/wiki/{id}/task-links", wikiId).header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"taskIds\":[" + taskId + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").value(taskId));
        mvc.perform(get("/api/wiki/{id}/versions", wikiId).header("Authorization", viewer.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get("/api/wiki/list/{projectId}", projectId).header("Authorization", viewer.authorization())
                        .param("keyword", "第二版").param("taskId", String.valueOf(taskId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(wikiId));

        long templateId = dataId(mvc.perform(post("/api/project/{projectId}/templates", projectId)
                        .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"交付模板\",\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.taskCount").value(1))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/project/create-from-template").header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"templateId\":" + templateId
                                + ",\"name\":\"模板项目\",\"startDate\":\"" + LocalDate.now().plusDays(3) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("模板项目"));

        mvc.perform(put("/api/task/{taskId}/recurrence", taskId).header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"frequency\":\"WEEKLY\",\"intervalValue\":1,\"weekdays\":[1],\"startDate\":\""
                                + LocalDate.now().plusDays(7) + "\",\"dueOffsetDays\":1,\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.frequency").value("WEEKLY"));

        String csv = "任务标题,状态,优先级,负责人用户名,开始日期,截止日期,预计工时,实际工时,标签,前置任务标题\r\n"
                + "CSV导入任务,TODO,MEDIUM," + ownerName + "," + LocalDate.now() + "," + LocalDate.now().plusDays(2) + ",8,,TESTING,\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "tasks.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/project/{projectId}/tasks/import/preview", projectId).file(file)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.validCount").value(1));

        mvc.perform(put("/api/user/notification-preferences").header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"owner" + suffix + "@example.com\",\"emailEnabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.emailEnabled").value(true))
                .andExpect(jsonPath("$.data.emailVerifiedAt").isEmpty());
        mvc.perform(get("/api/analytics/overview").header("Authorization", owner.authorization())
                        .param("projectId", String.valueOf(projectId)).param("from", LocalDate.now().minusDays(1).toString())
                        .param("to", LocalDate.now().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.projectTaskRanking[0].projectId").value(projectId));
        assertTrue(viewerId > 0);
    }

    @Test
    void productLabAcceptanceCapacityAndOperationsFlow() throws Exception {
        long suffix = System.nanoTime();
        String ownerName = "productOwner" + suffix;
        String reviewerName = "qualityReviewer" + suffix;
        String viewerName = "stakeholder" + suffix;
        register(ownerName, "OwnerPass@2026", "产品负责人", "PRODUCT_MANAGER");
        long reviewerId = register(reviewerName, "ReviewerPass@2026", "质量评审", "QA_TESTER");
        register(viewerName, "ViewerPass@2026", "业务观察员", "PROJECT_MANAGER");
        Session owner = login(ownerName, "OwnerPass@2026");
        Session reviewer = login(reviewerName, "ReviewerPass@2026");
        Session viewer = login(viewerName, "ViewerPass@2026");

        long projectId = dataId(mvc.perform(post("/api/project/create").header("Authorization", owner.authorization())
                        .param("name", "产品共创交付项目").param("description", "验证 AI 草稿、人工确认与质量闭环"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/project/{projectId}/members/invite", projectId).header("Authorization", owner.authorization())
                        .param("username", reviewerName).param("identity", "QA_TESTER").param("permission", "MEMBER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/api/project/{projectId}/members/invite", projectId).header("Authorization", owner.authorization())
                        .param("username", viewerName).param("identity", "PROJECT_MANAGER").param("permission", "VIEWER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));

        long taskId = dataId(mvc.perform(post("/api/task/create").header("Authorization", owner.authorization())
                        .param("projectId", String.valueOf(projectId)).param("title", "上线前质量验收")
                        .param("assigneeId", String.valueOf(owner.userId())).param("estimatedHours", "16")
                        .param("acceptanceCriteria", "关键路径可操作且无阻断错误"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        mvc.perform(get("/api/workspace/role-view").header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.identity").value("PRODUCT_MANAGER"))
                .andExpect(jsonPath("$.data.primaryAction").value("OPEN_PRODUCT_LAB"));
        mvc.perform(get("/api/workspace/role-view").header("Authorization", viewer.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.identity").value("VIEWER"));

        long conversationId = dataId(mvc.perform(post("/api/project/{projectId}/product-lab/conversations", projectId)
                        .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"MVP 范围讨论\",\"stage\":\"REQUIREMENT\",\"mode\":\"PRD_DRAFT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mode").value("PRD_DRAFT"))
                .andReturn().getResponse().getContentAsString());
        long artifactId = dataId(mvc.perform(post("/api/project/{projectId}/product-lab/artifacts", projectId)
                        .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":" + conversationId + ",\"type\":\"PRD\",\"title\":\"MVP PRD\",\"content\":\"# 目标\\n完成最小交付闭环\",\"status\":\"DRAFT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.versionNo").value(1))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(put("/api/project/{projectId}/product-lab/artifacts/{artifactId}", projectId, artifactId)
                        .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":" + conversationId + ",\"type\":\"PRD\",\"title\":\"MVP PRD v2\",\"content\":\"# 目标\\n补齐评审闭环\",\"status\":\"DRAFT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.versionNo").value(2));
        mvc.perform(get("/api/project/{projectId}/product-lab/artifacts/{artifactId}", projectId, artifactId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.versions.length()").value(2))
                .andExpect(jsonPath("$.data.versions[0].versionNo").value(2));

        String taskDraft = "{\"target\":\"TASKS\",\"tasks\":[{\"title\":\"共创生成任务\",\"description\":\"等待用户确认后创建\"}]}";
        mvc.perform(post("/api/project/{projectId}/product-lab/artifacts/{artifactId}/apply/preview", projectId, artifactId)
                        .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON).content(taskDraft))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.requiresConfirmation").value(true))
                .andExpect(jsonPath("$.data.willCreate").value(1));
        mvc.perform(get("/api/task/list/{projectId}", projectId).header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(post("/api/project/{projectId}/product-lab/artifacts/{artifactId}/apply", projectId, artifactId)
                        .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON).content(taskDraft))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("请先预览变更并确认应用"));
        String applied = mvc.perform(post("/api/project/{projectId}/product-lab/artifacts/{artifactId}/apply", projectId, artifactId)
                        .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"TASKS\",\"confirmed\":true,\"tasks\":[{\"title\":\"共创生成任务\",\"description\":\"已确认创建\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.createdIds.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        long generatedTaskId = objectMapper.readTree(applied).path("data").path("createdIds").get(0).asLong();
        mvc.perform(get("/api/task/list/{projectId}", projectId).header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + generatedTaskId + ")].startDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.data[?(@.id == " + generatedTaskId + ")].dueDate").value(LocalDate.now().plusDays(14).toString()));

        mvc.perform(post("/api/project/{projectId}/product-lab/artifacts", projectId)
                        .header("Authorization", viewer.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PRD\",\"title\":\"越权草稿\",\"content\":\"不应创建\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500));
        mvc.perform(post("/api/project/{projectId}/product-lab/artifacts/{artifactId}/publish", projectId, artifactId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedWikiId").isNumber());
        mvc.perform(get("/api/project/{projectId}/product-lab/artifacts", projectId)
                        .header("Authorization", viewer.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(artifactId));

        mvc.perform(put("/api/task/{taskId}/acceptance", taskId).header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"CONFIGURE\",\"reviewRequired\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("NOT_READY"));
        mvc.perform(put("/api/task/update").header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + taskId + ",\"status\":\"DONE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("该任务需要先提交并通过验收，不能直接完成"));
        long checklistId = dataId(mvc.perform(post("/api/task/{taskId}/acceptance/checklist", taskId)
                        .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"核心流程通过\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(put("/api/task/{taskId}/acceptance", taskId).header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"SUBMIT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("请先完成全部验收清单项"));
        mvc.perform(put("/api/task/{taskId}/acceptance/checklist/{itemId}", taskId, checklistId)
                        .header("Authorization", owner.authorization()).param("checked", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.checked").value(true));
        mvc.perform(post("/api/task/{taskId}/time-entries", taskId).header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDate\":\"" + LocalDate.now() + "\",\"hours\":4.5,\"note\":\"完成联调\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hours").value(4.5));
        mvc.perform(put("/api/task/{taskId}/acceptance", taskId).header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"SUBMIT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
        mvc.perform(put("/api/task/{taskId}/acceptance", taskId).header("Authorization", reviewer.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"START\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_REVIEW"));
        mvc.perform(put("/api/task/{taskId}/acceptance", taskId).header("Authorization", reviewer.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REJECT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500));
        mvc.perform(put("/api/task/{taskId}/acceptance", taskId).header("Authorization", reviewer.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REJECT\",\"comment\":\"缺少移动端证据\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));
        mvc.perform(put("/api/task/{taskId}/acceptance", taskId).header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"SUBMIT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
        mvc.perform(put("/api/task/{taskId}/acceptance", taskId).header("Authorization", reviewer.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"PASS\",\"comment\":\"验收通过\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PASSED"));
        mvc.perform(put("/api/task/update").header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + taskId + ",\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("已完成任务不能直接退回；发现 Bug 时请由测试工程师执行打回"));
        mvc.perform(put("/api/task/{taskId}/acceptance", taskId).header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"RETURN_FOR_FIX\",\"comment\":\"回归发现问题\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("只有测试工程师可以将已完成任务打回修改"));
        mvc.perform(put("/api/task/{taskId}/acceptance", taskId).header("Authorization", reviewer.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"RETURN_FOR_FIX\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("打回修改时必须填写 Bug 原因"));
        mvc.perform(put("/api/task/{taskId}/acceptance", taskId).header("Authorization", reviewer.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"RETURN_FOR_FIX\",\"comment\":\"移动端提交后出现重复记录\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.reviews[0].action").value("RETURN_FOR_FIX"));
        mvc.perform(get("/api/task/list/{projectId}", projectId).header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + taskId + ")].status").value("IN_PROGRESS"));

        mvc.perform(put("/api/project/{projectId}/capacity", projectId).header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":" + reviewerId + ",\"weeklyHours\":32}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.weeklyHours").value(32));
        long capacityExceptionId = dataId(mvc.perform(post("/api/project/{projectId}/capacity/exceptions", projectId)
                        .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + reviewerId + ",\"exceptionDate\":\"" + LocalDate.now() + "\",\"availableHours\":0,\"reason\":\"请假\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.availableHours").value(0))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/project/{projectId}/capacity", projectId).header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.userId == " + reviewerId + ")].exceptions.length()").value(1));
        mvc.perform(delete("/api/project/{projectId}/capacity/exceptions/{exceptionId}", projectId, capacityExceptionId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/api/project/{projectId}/capacity/exceptions", projectId)
                        .header("Authorization", owner.authorization()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + reviewerId + ",\"exceptionDate\":\"" + LocalDate.now() + "\",\"availableHours\":4,\"reason\":\"半天培训\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/project/{projectId}/executive-summary", projectId).header("Authorization", viewer.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.health.level").exists())
                .andExpect(jsonPath("$.data.schedule").isMap()).andExpect(jsonPath("$.data.capacity").isArray());

        Session admin = login("admin", "TestAdmin@2026");
        mvc.perform(get("/api/workspace/role-view").header("Authorization", admin.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.identity").value("SYSTEM_ADMIN"));
        mvc.perform(get("/api/admin/system-overview").header("Authorization", admin.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.migrationVersion").value("12"));
        mvc.perform(get("/api/admin/audit-events").header("Authorization", admin.authorization())
                        .param("projectId", String.valueOf(projectId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(6)));
        mvc.perform(get("/api/admin/login-events").header("Authorization", admin.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)));

        mvc.perform(delete("/api/project/{id}", projectId).header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(delete("/api/recycle-bin/PROJECT/{id}/permanent", projectId).header("Authorization", admin.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_product_conversation WHERE project_id=?", Integer.class, projectId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_product_artifact WHERE project_id=?", Integer.class, projectId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_acceptance_checklist WHERE project_id=?", Integer.class, projectId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_task_review WHERE project_id=?", Integer.class, projectId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_time_entry WHERE project_id=?", Integer.class, projectId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_member_capacity WHERE project_id=?", Integer.class, projectId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pm_capacity_exception WHERE project_id=?", Integer.class, projectId));
    }

    @Test
    void jsonAuthenticationProjectCapabilitiesAndOwnerOnlyDeletion() throws Exception {
        long suffix = System.nanoTime();
        String ownerName = "feedbackOwner" + suffix;
        String adminName = "feedbackAdmin" + suffix;

        mvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ownerName + "\",\"password\":\"OwnerPass@2026\",\"nickname\":\"提示负责人\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));

        String ownerLogin = mvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ownerName + "\",\"password\":\"OwnerPass@2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        JsonNode ownerData = objectMapper.readTree(ownerLogin).path("data");
        Session owner = new Session(ownerData.path("token").asText(), ownerData.path("userId").asLong());

        long projectId = dataId(mvc.perform(post("/api/project/create")
                        .header("Authorization", owner.authorization()).param("name", "权限提示测试"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        register(adminName, "AdminPass@2026", "项目管理员", "PROJECT_MANAGER");
        mvc.perform(post("/api/project/{projectId}/members/invite", projectId)
                        .header("Authorization", owner.authorization()).param("username", adminName)
                        .param("identity", "PROJECT_MANAGER").param("permission", "PROJECT_ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        Session projectAdmin = login(adminName, "AdminPass@2026");

        mvc.perform(get("/api/project/list").header("Authorization", owner.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].owner").value(true))
                .andExpect(jsonPath("$.data[0].myPermission").value("PROJECT_ADMIN"));
        mvc.perform(get("/api/project/list").header("Authorization", projectAdmin.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].owner").value(false))
                .andExpect(jsonPath("$.data[0].myPermission").value("PROJECT_ADMIN"));
        mvc.perform(delete("/api/project/{id}", projectId).header("Authorization", projectAdmin.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("只有项目负责人可以删除项目"));
    }

    private long register(String username, String password, String nickname) throws Exception {
        return register(username, password, nickname, "QA_TESTER");
    }

    private long register(String username, String password, String nickname, String identity) throws Exception {
        String response = mvc.perform(post("/api/user/register")
                        .param("username", username).param("password", password)
                        .param("nickname", nickname).param("identity", identity))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return dataId(response);
    }

    private Session login(String username, String password) throws Exception {
        String response = mvc.perform(post("/api/user/login")
                        .param("username", username).param("password", password))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        return new Session(data.path("token").asText(), data.path("userId").asLong());
    }

    private long dataId(String response) throws Exception {
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private long unreadCount(Session session) throws Exception {
        String response = mvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", session.authorization()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("count").asLong();
    }

    private ConnectedSocket connect(String path) throws Exception {
        LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
        WebSocketSession session = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession ignored, TextMessage message) {
                messages.offer(message.getPayload());
            }
        }, "ws://127.0.0.1:" + serverPort + path).get(10, TimeUnit.SECONDS);
        return new ConnectedSocket(session, messages);
    }

    private boolean awaitMessage(ConnectedSocket socket, String expectedType) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            String message = socket.messages().poll(250, TimeUnit.MILLISECONDS);
            if (message != null && message.contains(expectedType)) return true;
        }
        return false;
    }

    private record Session(String token, long userId) {
        String authorization() {
            return "Bearer " + token;
        }
    }

    private record ConnectedSocket(WebSocketSession session, LinkedBlockingQueue<String> messages) {}
}
