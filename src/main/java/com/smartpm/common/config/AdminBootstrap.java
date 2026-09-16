package com.smartpm.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartpm.entity.User;
import com.smartpm.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AdminBootstrap {

    private final JdbcTemplate jdbcTemplate;
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${smartpm.initial-admin-password:SmartPM@2026}")
    private String initialAdminPassword;

    @Bean
    ApplicationRunner initializeSystemAdmin() {
        return args -> {
            addColumnIfMissing("ALTER TABLE sys_user ADD COLUMN system_role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '系统权限: ADMIN/USER' AFTER identity");
            addColumnIfMissing("ALTER TABLE sys_user ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态: ACTIVE/DISABLED' AFTER system_role");
            jdbcTemplate.update("UPDATE sys_user SET system_role = 'USER' WHERE system_role IS NULL OR system_role = ''");
            jdbcTemplate.update("UPDATE sys_user SET status = 'ACTIVE' WHERE status IS NULL OR status = ''");
            ensureAdminAccount();
        };
    }

    private void ensureAdminAccount() {
        User admin = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
        if (admin == null) {
            if (initialAdminPassword == null || initialAdminPassword.length() < 8) {
                throw new IllegalStateException("INITIAL_ADMIN_PASSWORD 至少需要 8 位");
            }
            admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(initialAdminPassword));
            admin.setNickname("系统管理员");
            admin.setSystemRole("ADMIN");
            admin.setStatus("ACTIVE");
            admin.setCreatedAt(LocalDateTime.now());
            admin.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(admin);
            log.info("默认系统管理员账号 admin 已创建");
            return;
        }
        // 后续启动不覆盖管理员密码或状态，避免停用账号被意外恢复。
        if (!"ADMIN".equals(admin.getSystemRole())) {
            admin.setSystemRole("ADMIN");
            admin.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(admin);
        }
    }

    private void addColumnIfMissing(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ignored) {
            // MySQL 在列已存在时会报错；此处可安全忽略，保证旧数据库可平滑升级。
        }
    }
}
