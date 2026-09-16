package com.smartpm.controller;
import com.smartpm.common.result.R;
import com.smartpm.dto.*;
import com.smartpm.entity.Project;
import com.smartpm.entity.ProjectTemplate;
import com.smartpm.service.ProjectTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/project") @RequiredArgsConstructor
public class ProjectTemplateController {
    private final ProjectTemplateService templateService;
    @GetMapping("/templates") public R<List<ProjectTemplate>> list() { return R.ok(templateService.list()); }
    @PostMapping("/{projectId}/templates") public R<ProjectTemplate> create(@PathVariable Long projectId,
            @Valid @RequestBody ProjectTemplateCreateDTO dto) { return R.ok(templateService.createFromProject(projectId, dto)); }
    @PostMapping("/create-from-template") public R<Project> createProject(@Valid @RequestBody ProjectFromTemplateDTO dto) {
        return R.ok(templateService.createProject(dto));
    }
    @DeleteMapping("/templates/{templateId}") public R<Void> delete(@PathVariable Long templateId) {
        templateService.delete(templateId); return R.ok();
    }
}
