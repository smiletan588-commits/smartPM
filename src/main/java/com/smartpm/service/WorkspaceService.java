package com.smartpm.service;

import com.smartpm.vo.GlobalSearchVO;
import com.smartpm.vo.WorkspaceOverviewVO;
import com.smartpm.vo.WorkspaceTaskPageVO;

public interface WorkspaceService {
    WorkspaceOverviewVO overview();
    WorkspaceTaskPageVO tasks(String scope, String keyword, Long projectId, String status, int page, int size);
    GlobalSearchVO search(String keyword, int limit);
}
