package com.smartpm.service;
import com.smartpm.dto.*;
import com.smartpm.entity.Project;
import com.smartpm.entity.ProjectTemplate;
import java.util.List;
public interface ProjectTemplateService {
    List<ProjectTemplate> list();
    ProjectTemplate createFromProject(Long projectId, ProjectTemplateCreateDTO dto);
    Project createProject(ProjectFromTemplateDTO dto);
    void delete(Long templateId);
}
