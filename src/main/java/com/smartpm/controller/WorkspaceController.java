package com.smartpm.controller;

import com.smartpm.common.result.R;
import com.smartpm.service.WorkspaceService;
import com.smartpm.service.RoleWorkspaceService;
import com.smartpm.vo.GlobalSearchVO;
import com.smartpm.vo.WorkspaceOverviewVO;
import com.smartpm.vo.WorkspaceTaskPageVO;
import lombok.RequiredArgsConstructor;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkspaceController {
    private final WorkspaceService workspaceService;
    private final RoleWorkspaceService roleWorkspaceService;

    @GetMapping("/workspace/role-view")
    public R<Map<String, Object>> roleView() {
        return R.ok(roleWorkspaceService.roleView());
    }

    @GetMapping("/workspace/overview")
    public R<WorkspaceOverviewVO> overview() {
        return R.ok(workspaceService.overview());
    }

    @GetMapping("/workspace/tasks")
    public R<WorkspaceTaskPageVO> tasks(@RequestParam(required = false) String scope,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Long projectId,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return R.ok(workspaceService.tasks(scope, keyword, projectId, status, page, size));
    }

    @GetMapping("/search")
    public R<GlobalSearchVO> search(@RequestParam String q,
                                    @RequestParam(defaultValue = "8") int limit) {
        return R.ok(workspaceService.search(q, limit));
    }
}
