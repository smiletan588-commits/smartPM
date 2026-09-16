package com.smartpm.controller;

import com.smartpm.common.result.R;
import com.smartpm.service.UserService;
import com.smartpm.service.ManagementOperationsService;
import com.smartpm.service.AuditService;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.vo.AdminUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final ManagementOperationsService managementService;
    private final AuditService auditService;

    @GetMapping("/audit-events")
    public R<List<Map<String, Object>>> auditEvents(@RequestParam(required = false) Long projectId,
                                                    @RequestParam(required = false) String category,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "50") int size) {
        return R.ok(managementService.auditEvents(projectId, category, page, size));
    }

    @GetMapping("/login-events")
    public R<List<Map<String, Object>>> loginEvents(@RequestParam(required = false) Boolean success,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "50") int size) {
        return R.ok(managementService.loginEvents(success, page, size));
    }

    @GetMapping("/system-overview")
    public R<Map<String, Object>> systemOverview() {
        return R.ok(managementService.systemOverview());
    }

    @GetMapping("/users")
    public R<List<AdminUserVO>> listUsers() {
        return R.ok(userService.listUsersForAdmin());
    }

    @PutMapping("/users/{userId}/status")
    public R<Void> updateStatus(@PathVariable Long userId, @RequestParam String status) {
        userService.updateUserStatus(userId, status);
        auditService.record(null, "ADMIN", "USER_STATUS_UPDATED", "USER", userId,
                "管理员更新账号状态为 " + status, Map.of("actorId", UserHolder.getUserId()));
        return R.ok();
    }

    @PutMapping("/users/{userId}/role")
    public R<Void> updateRole(@PathVariable Long userId, @RequestParam String systemRole) {
        userService.updateSystemRole(userId, systemRole);
        auditService.record(null, "ADMIN", "USER_ROLE_UPDATED", "USER", userId,
                "管理员更新系统权限为 " + systemRole, Map.of("actorId", UserHolder.getUserId()));
        return R.ok();
    }

    @PutMapping("/users/{userId}/password")
    public R<Void> resetPassword(@PathVariable Long userId, @RequestParam String password) {
        userService.resetUserPassword(userId, password);
        auditService.record(null, "ADMIN", "USER_PASSWORD_RESET", "USER", userId,
                "管理员重置账号密码", Map.of("actorId", UserHolder.getUserId()));
        return R.ok();
    }
}
