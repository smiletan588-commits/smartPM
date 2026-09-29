package com.smartpm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.dto.AiPlanningContentDTO;
import com.smartpm.dto.AiPlanningDraftVO;
import com.smartpm.dto.AiPlanningInputDTO;
import com.smartpm.entity.Project;
import com.smartpm.entity.Task;
import com.smartpm.entity.Wiki;
import com.smartpm.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiPlanningDraftServiceTest {
    @Mock JdbcTemplate jdbc;
    @Spy ObjectMapper mapper = new ObjectMapper();
    @Mock AIService aiService;
    @Mock ProjectService projectService;
    @Mock ProjectMapper projectMapper;
    @Mock TaskMapper taskMapper;
    @Mock TaskDependencyMapper dependencyMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock WikiMapper wikiMapper;
    @Mock ProjectMilestoneMapper milestoneMapper;
    @Mock MilestoneTaskMapper milestoneTaskMapper;
    @Mock CollaborationService collaborationService;
    @Mock RiskService riskService;
    @Mock AiOperationLogService operationLogService;
    @InjectMocks AiPlanningDraftService service;

    @BeforeEach
    void emptyProject() {
        lenient().when(taskMapper.selectList(any())).thenReturn(List.of());
        lenient().when(memberMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void projectNameAloneCannotTriggerAiOrWriteTasks() {
        Project project = new Project(); project.setId(20L); project.setName("活动管理");
        when(projectMapper.selectById(20L)).thenReturn(project);
        AiPlanningInputDTO input = new AiPlanningInputDTO();
        input.setMode("INIT"); input.setProjectType("GENERAL");
        assertThrows(BusinessException.class, () -> service.generate(20L, input, null));
        verifyNoInteractions(aiService, jdbc);
    }

    @Test
    void emptyDecompositionCanExplainWhyNoMoreTasksAreNeeded() {
        AiPlanningDraftVO draft = draft("DECOMPOSE", "SOFTWARE");
        draft.getContent().setNoSplitReason("任务已明确交付物和验收标准，无需继续拆解");
        Task parent = new Task(); parent.setId(5L); parent.setProjectId(20L);
        assertTrue(service.validate(draft, parent).isEmpty());
    }

    @Test
    void rejectsDuplicatesAndMissingDeliverables() {
        AiPlanningDraftVO draft = draft("INIT", "SOFTWARE");
        draft.getContent().getTasks().add(item("建立报名页面", "页面可以提交报名"));
        draft.getContent().getTasks().add(item("建立 报名页面", null));
        List<String> codes = service.validate(draft, null).stream().map(AiPlanningDraftVO.Issue::code).toList();
        assertTrue(codes.contains("DUPLICATE"));
        assertTrue(codes.contains("DELIVERABLE"));
    }

    @Test
    void generalProjectsCannotBeForcedIntoDeveloperRoles() {
        AiPlanningDraftVO draft = draft("PLAN", "GENERAL");
        AiPlanningContentDTO.Item item = item("组织志愿者培训", "培训签到表");
        item.setRecommendedRole("BACKEND_DEV");
        draft.getContent().getTasks().add(item);
        assertTrue(service.validate(draft, null).stream().anyMatch(issue -> issue.code().equals("ROLE_SCOPE") && issue.blocking()));
    }

    @Test
    void childTaskCannotPassParentDeadlineOrReferToFutureTask() {
        AiPlanningDraftVO draft = draft("DECOMPOSE", "SOFTWARE");
        AiPlanningContentDTO.Item item = item("实现报名表单", "可提交的表单");
        item.setStartDate("2026-10-01"); item.setDueDate("2026-10-20");
        item.setDependencyIndexes(List.of(1));
        draft.getContent().getTasks().add(item);
        Task parent = new Task(); parent.setId(5L); parent.setProjectId(20L);
        parent.setStartDate(LocalDate.of(2026, 10, 1)); parent.setDueDate(LocalDate.of(2026, 10, 15));
        List<String> codes = service.validate(draft, parent).stream().map(AiPlanningDraftVO.Issue::code).toList();
        assertTrue(codes.contains("PARENT_DATE"));
        assertTrue(codes.contains("DEPENDENCY"));
    }

    @Test
    void rejectsExplicitDependencyDateConflict() {
        AiPlanningDraftVO draft = draft("PLAN", "SOFTWARE");
        AiPlanningContentDTO.Item first = item("完成需求评审", "评审记录");
        first.setStartDate("2026-10-01"); first.setDueDate("2026-10-05");
        AiPlanningContentDTO.Item second = item("编写实现代码", "可运行的功能");
        second.setStartDate("2026-10-05"); second.setDueDate("2026-10-15");
        second.setDependencyIndexes(List.of(0));
        draft.getContent().getTasks().addAll(List.of(first, second));
        assertTrue(service.validate(draft, null).stream().anyMatch(issue -> issue.code().equals("DEPENDENCY_DATE") && issue.blocking()));
        second.setStartDate("2026-10-06");
        assertTrue(service.validate(draft, null).stream().noneMatch(issue -> issue.code().equals("DEPENDENCY_DATE")));
    }

    @Test
    void missingProjectTypeReturnsValidationError() {
        Project project = new Project(); project.setId(20L); project.setName("活动管理");
        when(projectMapper.selectById(20L)).thenReturn(project);
        AiPlanningInputDTO input = new AiPlanningInputDTO();
        input.setMode("INIT"); input.setGoal("举办活动"); input.setDeliverable("活动总结"); input.setScope("本季度");
        assertThrows(BusinessException.class, () -> service.generate(20L, input, null));
        verifyNoInteractions(aiService, jdbc);
    }

    @Test
    void mainTaskPastDueDateCannotBeSilentlyReplaced() {
        AiPlanningDraftVO draft = draft("INIT", "SOFTWARE");
        AiPlanningContentDTO.Item item = item("制作报名页面", "报名页面");
        item.setDueDate(LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1).toString());
        draft.getContent().getTasks().add(item);
        assertTrue(service.validate(draft, null).stream().anyMatch(issue -> issue.code().equals("DATE") && issue.blocking()));
    }

    @Test
    void crossProjectWikiCannotEnterPrompt() {
        Project project = new Project(); project.setId(20L); project.setName("活动管理");
        when(projectMapper.selectById(20L)).thenReturn(project);
        AiPlanningInputDTO input = new AiPlanningInputDTO();
        input.setMode("INIT"); input.setProjectType("GENERAL");
        input.setGoal("举办活动"); input.setDeliverable("活动总结"); input.setScope("本季度");
        input.setWikiIds(List.of(77L));
        Wiki otherProjectWiki = new Wiki(); otherProjectWiki.setId(77L); otherProjectWiki.setProjectId(21L);
        when(wikiMapper.selectBatchIds(List.of(77L))).thenReturn(List.of(otherProjectWiki));
        assertThrows(BusinessException.class, () -> service.generate(20L, input, null));
        verifyNoInteractions(aiService, jdbc);
    }

    @Test
    void acceptsArrayTagsFromValidModelJson() {
        AiPlanningContentDTO content = service.parse("""
            {"overview":"活动规划","tasks":[{"title":"组织活动","tags":["DESIGN","DOCUMENTATION"]}]}
            """);
        assertEquals("DESIGN,DOCUMENTATION", content.getTasks().get(0).getTags());
    }

    private AiPlanningDraftVO draft(String mode, String projectType) {
        AiPlanningDraftVO draft = new AiPlanningDraftVO();
        draft.setProjectId(20L); draft.setMode(mode);
        AiPlanningInputDTO input = new AiPlanningInputDTO(); input.setMode(mode); input.setProjectType(projectType);
        draft.setInput(input);
        AiPlanningContentDTO content = new AiPlanningContentDTO(); content.setTasks(new ArrayList<>());
        draft.setContent(content);
        return draft;
    }

    private AiPlanningContentDTO.Item item(String title, String deliverable) {
        AiPlanningContentDTO.Item item = new AiPlanningContentDTO.Item();
        item.setTitle(title); item.setDescription("完成工作"); item.setDeliverable(deliverable);
        item.setAcceptanceCriteria("能够检查结果"); item.setFitReason("属于目标范围");
        return item;
    }
}
