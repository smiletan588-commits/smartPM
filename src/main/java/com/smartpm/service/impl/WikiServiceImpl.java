package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.entity.Project;
import com.smartpm.entity.Wiki;
import com.smartpm.entity.WikiVersion;
import com.smartpm.entity.WikiTask;
import com.smartpm.entity.Task;
import com.smartpm.entity.User;
import com.smartpm.mapper.ProjectMapper;
import com.smartpm.mapper.WikiMapper;
import com.smartpm.mapper.WikiVersionMapper;
import com.smartpm.mapper.WikiTaskMapper;
import com.smartpm.mapper.TaskMapper;
import com.smartpm.mapper.UserMapper;
import com.smartpm.service.AIService;
import com.smartpm.service.ProjectService;
import com.smartpm.service.WikiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class WikiServiceImpl implements WikiService {

    private final WikiMapper wikiMapper;
    private final ProjectMapper projectMapper;
    private final AIService aiService;
    private final ProjectService projectService;
    private final WikiVersionMapper versionMapper;
    private final WikiTaskMapper wikiTaskMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;

    @Override
    public Wiki create(Long projectId, String title, String content) {
        projectService.assertProjectAccess(projectId, true);
        if (title == null || title.isBlank()) {
            throw new BusinessException("文档标题不能为空");
        }

        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }

        Wiki wiki = new Wiki();
        wiki.setProjectId(projectId);
        wiki.setTitle(title);
        wiki.setContent(content != null ? content : "");
        wiki.setCreatorId(UserHolder.getUserId());
        wiki.setCreateTime(LocalDateTime.now());
        wiki.setUpdateTime(LocalDateTime.now());

        wikiMapper.insert(wiki);
        saveVersion(wiki);
        return wiki;
    }

    @Override
    public List<Map<String, Object>> listByProject(Long projectId, String keyword, Long taskId) {
        projectService.assertProjectAccess(projectId, false);
        Set<Long> linkedWikiIds = null;
        if (taskId != null) {
            Task task = taskMapper.selectById(taskId);
            if (task == null || task.getDeletedAt() != null || !Objects.equals(projectId, task.getProjectId())) {
                throw new BusinessException("关联任务不存在或不属于当前项目");
            }
            linkedWikiIds = wikiTaskMapper.selectList(new LambdaQueryWrapper<WikiTask>().eq(WikiTask::getProjectId, projectId)
                    .eq(WikiTask::getTaskId, taskId)).stream().map(WikiTask::getWikiId).collect(Collectors.toSet());
        }
        List<Wiki> wikis = wikiMapper.selectList(
                new LambdaQueryWrapper<Wiki>()
                        .eq(Wiki::getProjectId, projectId)
                        .isNull(Wiki::getDeletedAt)
                        .orderByDesc(Wiki::getUpdateTime));
        String q = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        Set<Long> finalLinkedWikiIds = linkedWikiIds;
        return wikis.stream().map(w -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", w.getId());
            m.put("title", w.getTitle());
            m.put("updateTime", w.getUpdateTime());
            User editor = latestEditor(w.getId());
            m.put("editorName", editor == null ? null : editor.getNickname() == null ? editor.getUsername() : editor.getNickname());
            m.put("taskIds", wikiTaskMapper.selectList(new LambdaQueryWrapper<WikiTask>().eq(WikiTask::getWikiId, w.getId()))
                    .stream().map(WikiTask::getTaskId).toList());
            return m;
        }).filter(m -> (finalLinkedWikiIds == null || finalLinkedWikiIds.contains((Long) m.get("id")))
                && (q.isEmpty() || String.valueOf(m.get("title")).toLowerCase(Locale.ROOT).contains(q)
                || containsContent((Long) m.get("id"), q))).collect(Collectors.toList());
    }

    @Override
    public Wiki getById(Long id) {
        Wiki wiki = wikiMapper.selectById(id);
        if (wiki == null || wiki.getDeletedAt() != null) {
            throw new BusinessException("文档不存在");
        }
        projectService.assertProjectAccess(wiki.getProjectId(), false);
        return wiki;
    }

    @Override
    @Transactional
    public Wiki update(Long id, String title, String content) {
        Wiki wiki = wikiMapper.selectById(id);
        if (wiki == null || wiki.getDeletedAt() != null) {
            throw new BusinessException("文档不存在");
        }
        projectService.assertProjectAccess(wiki.getProjectId(), true);
        if (title != null && !title.isBlank()) {
            wiki.setTitle(title);
        }
        if (content != null) {
            wiki.setContent(content);
        }
        wiki.setUpdateTime(LocalDateTime.now());
        wikiMapper.updateById(wiki);
        saveVersion(wiki);
        return wiki;
    }

    @Override
    public List<WikiVersion> listVersions(Long id) {
        Wiki wiki = getById(id);
        List<WikiVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<WikiVersion>()
                .eq(WikiVersion::getWikiId, id).orderByDesc(WikiVersion::getVersionNo));
        Set<Long> editorIds = versions.stream().map(WikiVersion::getEditorId).collect(Collectors.toSet());
        Map<Long, String> names = editorIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(editorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getNickname() == null ? u.getUsername() : u.getNickname()));
        versions.forEach(v -> v.setEditorName(names.getOrDefault(v.getEditorId(), "未知用户")));
        return versions;
    }

    @Override
    @Transactional
    public Wiki restoreVersion(Long id, Long versionId) {
        Wiki wiki = getById(id);
        projectService.assertProjectAccess(wiki.getProjectId(), true);
        WikiVersion version = versionMapper.selectById(versionId);
        if (version == null || !Objects.equals(id, version.getWikiId())) throw new BusinessException("文档版本不存在");
        return update(id, version.getTitle(), version.getContent());
    }

    @Override
    @Transactional
    public List<Long> updateTaskLinks(Long id, List<Long> taskIds) {
        Wiki wiki = getById(id);
        projectService.assertProjectAccess(wiki.getProjectId(), true);
        List<Long> normalized = taskIds == null ? List.of() : taskIds.stream().filter(Objects::nonNull).distinct().toList();
        if (normalized.size() > 100) throw new BusinessException("单篇文档最多关联 100 个任务");
        if (!normalized.isEmpty()) {
            List<Task> tasks = taskMapper.selectBatchIds(normalized).stream().filter(t -> t.getDeletedAt() == null).toList();
            if (tasks.size() != normalized.size() || tasks.stream().anyMatch(t -> !Objects.equals(wiki.getProjectId(), t.getProjectId()))) {
                throw new BusinessException("只能关联当前项目的有效任务");
            }
        }
        wikiTaskMapper.delete(new LambdaQueryWrapper<WikiTask>().eq(WikiTask::getWikiId, id));
        for (Long taskId : normalized) {
            WikiTask link = new WikiTask(); link.setWikiId(id); link.setTaskId(taskId);
            link.setProjectId(wiki.getProjectId()); link.setCreatedAt(LocalDateTime.now()); wikiTaskMapper.insert(link);
        }
        return normalized;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Wiki wiki = wikiMapper.selectById(id);
        if (wiki == null || wiki.getDeletedAt() != null) {
            throw new BusinessException("文档不存在");
        }
        projectService.assertProjectAccess(wiki.getProjectId(), true);
        wiki.setDeletedAt(LocalDateTime.now());
        wiki.setDeletedBy(UserHolder.getUserId());
        wiki.setUpdateTime(LocalDateTime.now());
        wikiMapper.updateById(wiki);
    }

    @Override
    public Flux<String> aiCopilot(String prompt, String text) {
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException("AI 指令不能为空");
        }
        if (text == null || text.isBlank()) {
            throw new BusinessException("待处理的文本不能为空");
        }

        String fullPrompt = buildCopilotPrompt(prompt, text);
        log.info("[AI-Copilot] Prompt 长度: {} 字符，开始调用 AI...", fullPrompt.length());
        return aiService.streamChat(fullPrompt);
    }

    private String buildCopilotPrompt(String userInstruction, String originalText) {
        return "作为一名资深的文档编辑，请根据用户指令【" + userInstruction +
                "】，对以下文本进行处理，直接输出处理后的结果，不要带多余的解释：\n\n" + originalText;
    }

    private void saveVersion(Wiki wiki) {
        WikiVersion latest = versionMapper.selectOne(new LambdaQueryWrapper<WikiVersion>().eq(WikiVersion::getWikiId, wiki.getId())
                .orderByDesc(WikiVersion::getVersionNo).last("LIMIT 1"));
        WikiVersion version = new WikiVersion(); version.setWikiId(wiki.getId()); version.setProjectId(wiki.getProjectId());
        version.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1); version.setTitle(wiki.getTitle());
        version.setContent(wiki.getContent()); version.setEditorId(UserHolder.getUserId()); version.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(version);
    }

    private User latestEditor(Long wikiId) {
        WikiVersion latest = versionMapper.selectOne(new LambdaQueryWrapper<WikiVersion>().eq(WikiVersion::getWikiId, wikiId)
                .orderByDesc(WikiVersion::getVersionNo).last("LIMIT 1"));
        return latest == null ? null : userMapper.selectById(latest.getEditorId());
    }

    private boolean containsContent(Long wikiId, String q) {
        Wiki wiki = wikiMapper.selectById(wikiId);
        return wiki != null && wiki.getContent() != null && wiki.getContent().toLowerCase(Locale.ROOT).contains(q);
    }
}
