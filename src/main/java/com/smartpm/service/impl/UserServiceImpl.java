package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.JWTUtil;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.entity.User;
import com.smartpm.mapper.UserMapper;
import com.smartpm.service.UserService;
import com.smartpm.service.AuditService;
import com.smartpm.vo.AdminUserVO;
import com.smartpm.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JWTUtil jwtUtil;
    private final AuditService auditService;

    private static final Set<String> VALID_IDENTITIES = Set.of(
            "PROJECT_MANAGER", "PRODUCT_MANAGER", "FRONTEND_DEV", "BACKEND_DEV", "QA_TESTER", "UI_DESIGNER"
    );
    private static final Set<String> VALID_SYSTEM_ROLES = Set.of("ADMIN", "USER");
    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "DISABLED");

    @Override
    public User register(String username, String password, String nickname, String identity) {
        if (username == null || username.isBlank()) throw new BusinessException("用户名不能为空");
        if (password == null || password.length() < 8) throw new BusinessException("密码至少需要 8 位");
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }

        if (identity != null && !identity.isBlank()) {
            String upper = identity.toUpperCase();
            if (!VALID_IDENTITIES.contains(upper)) {
                throw new BusinessException("无效的专业身份，可选值: " + String.join(", ", VALID_IDENTITIES));
            }
            identity = upper;
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname != null ? nickname : username);
        user.setIdentity(identity);
        user.setSystemRole("USER");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userMapper.insert(user);
        return user;
    }

    @Override
    public LoginVO login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            auditService.recordLogin(null, username, false, "用户名或密码错误");
            throw new BusinessException("用户名或密码错误");
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            auditService.recordLogin(null, username, false, "用户名或密码错误");
            throw new BusinessException("用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            auditService.recordLogin(user.getId(), username, false, "账号已停用");
            throw new BusinessException("账号已停用，请联系系统管理员");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            auditService.recordLogin(user.getId(), username, false, "用户名或密码错误");
            throw new BusinessException("用户名或密码错误");
        }

        String token = jwtUtil.generate(user.getId(), user.getUsername());
        auditService.recordLogin(user.getId(), username, true, null);
        return new LoginVO(token, user.getId(), user.getUsername(), user.getIdentity(), user.getSystemRole());
    }

    @Override
    public void updateIdentity(Long userId, String identity) {
        if (identity == null || identity.isBlank()) {
            throw new BusinessException("专业身份不能为空");
        }
        String upper = identity.toUpperCase();
        if (!VALID_IDENTITIES.contains(upper)) {
            throw new BusinessException("无效的专业身份，可选值: " + String.join(", ", VALID_IDENTITIES));
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setIdentity(upper);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public boolean isSystemAdmin(Long userId) {
        if (userId == null) return false;
        User user = userMapper.selectById(userId);
        return user != null && "ADMIN".equals(user.getSystemRole()) && "ACTIVE".equals(user.getStatus());
    }

    @Override
    public List<AdminUserVO> listUsersForAdmin() {
        requireSystemAdmin();
        return userMapper.selectList(new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt))
                .stream().map(AdminUserVO::from).toList();
    }

    @Override
    public void updateUserStatus(Long userId, String status) {
        requireSystemAdmin();
        String normalized = normalize(status, VALID_STATUSES, "无效的账号状态");
        User target = requireUser(userId);
        if (target.getId().equals(UserHolder.getUserId()) && "DISABLED".equals(normalized)) {
            throw new BusinessException("不能停用当前登录的管理员账号");
        }
        if ("DISABLED".equals(normalized) && "ADMIN".equals(target.getSystemRole()) && countActiveAdmins() <= 1) {
            throw new BusinessException("至少需要保留一个启用中的系统管理员");
        }
        target.setStatus(normalized);
        target.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(target);
    }

    @Override
    public void updateSystemRole(Long userId, String systemRole) {
        requireSystemAdmin();
        String normalized = normalize(systemRole, VALID_SYSTEM_ROLES, "无效的系统权限");
        User target = requireUser(userId);
        if (target.getId().equals(UserHolder.getUserId()) && "USER".equals(normalized)) {
            throw new BusinessException("不能移除当前登录账号的管理员权限");
        }
        if ("USER".equals(normalized) && "ADMIN".equals(target.getSystemRole()) && "ACTIVE".equals(target.getStatus()) && countActiveAdmins() <= 1) {
            throw new BusinessException("至少需要保留一个启用中的系统管理员");
        }
        target.setSystemRole(normalized);
        target.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(target);
    }

    @Override
    public void resetUserPassword(Long userId, String password) {
        requireSystemAdmin();
        if (password == null || password.length() < 8) {
            throw new BusinessException("新密码至少需要 8 位");
        }
        User target = requireUser(userId);
        target.setPassword(passwordEncoder.encode(password));
        target.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(target);
    }

    private void requireSystemAdmin() {
        if (!isSystemAdmin(UserHolder.getUserId())) {
            throw new BusinessException("仅系统管理员可以执行此操作");
        }
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        return user;
    }

    private long countActiveAdmins() {
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getSystemRole, "ADMIN").eq(User::getStatus, "ACTIVE"));
    }

    private String normalize(String value, Set<String> allowed, String message) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!allowed.contains(normalized)) throw new BusinessException(message);
        return normalized;
    }
}
