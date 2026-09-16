package com.smartpm.service;

import com.smartpm.entity.Wiki;
import com.smartpm.entity.WikiVersion;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

public interface WikiService {

    Wiki create(Long projectId, String title, String content);

    default List<Map<String, Object>> listByProject(Long projectId) { return listByProject(projectId, null, null); }
    List<Map<String, Object>> listByProject(Long projectId, String keyword, Long taskId);

    Wiki getById(Long id);

    Wiki update(Long id, String title, String content);

    List<WikiVersion> listVersions(Long id);

    Wiki restoreVersion(Long id, Long versionId);

    List<Long> updateTaskLinks(Long id, List<Long> taskIds);

    void delete(Long id);

    Flux<String> aiCopilot(String prompt, String text);
}
