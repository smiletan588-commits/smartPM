package com.smartpm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartpm.common.exception.BusinessException;
import com.smartpm.common.utils.UserHolder;
import com.smartpm.dto.NotificationPreferenceDTO;
import com.smartpm.entity.*;
import com.smartpm.mapper.*;
import com.smartpm.service.EmailNotificationService;
import com.smartpm.vo.NotificationPreferenceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j @Service @RequiredArgsConstructor
public class EmailNotificationServiceImpl implements EmailNotificationService {
    private final UserMapper userMapper;
    private final NotificationPreferenceMapper preferenceMapper;
    private final EmailVerificationMapper verificationMapper;
    private final EmailOutboxMapper outboxMapper;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${smartpm.frontend-url:http://localhost:3000}") private String frontendUrl;
    @Value("${spring.mail.username:}") private String fromAddress;

    @Override
    public NotificationPreferenceVO getPreference() {
        return toVO(requireUser(), preference(UserHolder.getUserId()));
    }

    @Override
    @Transactional
    public NotificationPreferenceVO savePreference(NotificationPreferenceDTO dto) {
        User user = requireUser();
        if (dto.getEmail() != null) {
            String email = dto.getEmail().trim().toLowerCase(Locale.ROOT);
            if (email.isBlank()) { user.setEmail(null); user.setEmailVerifiedAt(null); }
            else if (!Objects.equals(email, user.getEmail())) {
                if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email).ne(User::getId, user.getId())) > 0) {
                    throw new BusinessException("该邮箱已被其他账号使用");
                }
                user.setEmail(email); user.setEmailVerifiedAt(null);
            }
            user.setUpdatedAt(LocalDateTime.now()); userMapper.updateById(user);
        }
        NotificationPreference pref = preference(user.getId());
        if (dto.getEmailEnabled() != null) pref.setEmailEnabled(dto.getEmailEnabled());
        if (dto.getAssignmentEnabled() != null) pref.setAssignmentEnabled(dto.getAssignmentEnabled());
        if (dto.getMentionEnabled() != null) pref.setMentionEnabled(dto.getMentionEnabled());
        if (dto.getDeadlineEnabled() != null) pref.setDeadlineEnabled(dto.getDeadlineEnabled());
        if (dto.getRiskEnabled() != null) pref.setRiskEnabled(dto.getRiskEnabled());
        pref.setUpdatedAt(LocalDateTime.now()); preferenceMapper.updateById(pref);
        return toVO(user, pref);
    }

    @Override
    @Transactional
    public void requestVerification() {
        User user = requireUser();
        if (user.getEmail() == null || user.getEmail().isBlank()) throw new BusinessException("请先填写邮箱地址");
        if (user.getEmailVerifiedAt() != null) throw new BusinessException("邮箱已经验证");
        String token = UUID.randomUUID() + "-" + UUID.randomUUID();
        EmailVerification item = new EmailVerification(); item.setUserId(user.getId()); item.setEmail(user.getEmail());
        item.setTokenHash(hash(token)); item.setExpiresAt(LocalDateTime.now().plusHours(24)); item.setCreatedAt(LocalDateTime.now());
        verificationMapper.insert(item);
        enqueueRaw(user.getId(), user.getEmail(), "验证 SmartPM 邮箱",
                "请在 24 小时内打开以下地址完成验证：\n\n" + frontendUrl + "/dashboard?verifyEmail=" + token,
                "verify:" + item.getId());
    }

    @Override
    @Transactional
    public void verify(String token) {
        EmailVerification item = verificationMapper.selectOne(new LambdaQueryWrapper<EmailVerification>()
                .eq(EmailVerification::getTokenHash, hash(token)).isNull(EmailVerification::getUsedAt));
        if (item == null || item.getExpiresAt().isBefore(LocalDateTime.now())) throw new BusinessException("邮箱验证链接无效或已过期");
        if (!Objects.equals(item.getUserId(), UserHolder.getUserId())) throw new BusinessException("邮箱验证链接不属于当前账号");
        User user = requireUser();
        if (!Objects.equals(user.getEmail(), item.getEmail())) throw new BusinessException("邮箱地址已经变更，请重新验证");
        item.setUsedAt(LocalDateTime.now()); verificationMapper.updateById(item);
        user.setEmailVerifiedAt(LocalDateTime.now()); user.setUpdatedAt(LocalDateTime.now()); userMapper.updateById(user);
    }

    @Override
    public void sendTest() {
        User user = requireUser();
        if (user.getEmail() == null || user.getEmailVerifiedAt() == null) throw new BusinessException("请先完成邮箱验证");
        enqueueRaw(user.getId(), user.getEmail(), "SmartPM 邮件通知测试", "邮件通知已正确配置。", "test:" + user.getId() + ":" + LocalDateTime.now());
    }

    @Override
    public void enqueue(Notification notification) {
        User user = userMapper.selectById(notification.getUserId());
        if (user == null || user.getEmail() == null || user.getEmailVerifiedAt() == null) return;
        NotificationPreference pref = preference(user.getId());
        if (!Boolean.TRUE.equals(pref.getEmailEnabled()) || !enabled(pref, notification.getType())) return;
        String route = notification.getProjectId() == null ? frontendUrl + "/dashboard"
                : frontendUrl + "/project/" + notification.getProjectId() + (notification.getTaskId() == null ? "" : "?task=" + notification.getTaskId());
        enqueueRaw(user.getId(), user.getEmail(), notification.getTitle(), notification.getContent() + "\n\n查看详情：" + route,
                "notification:" + notification.getId());
    }

    @Override
    @Scheduled(fixedDelay = 60000)
    public void deliverPending() {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || fromAddress == null || fromAddress.isBlank()) return;
        List<EmailOutbox> items = outboxMapper.selectList(new LambdaQueryWrapper<EmailOutbox>()
                .in(EmailOutbox::getStatus, List.of("PENDING", "FAILED")).le(EmailOutbox::getNextAttemptAt, LocalDateTime.now())
                .lt(EmailOutbox::getAttempts, 5).orderByAsc(EmailOutbox::getCreatedAt).last("LIMIT 50"));
        for (EmailOutbox item : items) {
            try {
                SimpleMailMessage mail = new SimpleMailMessage(); mail.setFrom(fromAddress); mail.setTo(item.getRecipient());
                mail.setSubject(item.getSubject()); mail.setText(item.getBody()); sender.send(mail);
                item.setStatus("SENT"); item.setSentAt(LocalDateTime.now()); item.setLastError(null);
            } catch (Exception e) {
                item.setAttempts(item.getAttempts() + 1); item.setStatus("FAILED");
                item.setNextAttemptAt(LocalDateTime.now().plusMinutes(Math.min(60, 1L << Math.min(5, item.getAttempts()))));
                item.setLastError(compact(e.getMessage())); log.warn("邮件发送失败 outboxId={}: {}", item.getId(), e.getMessage());
            }
            outboxMapper.updateById(item);
        }
    }

    private NotificationPreference preference(Long userId) {
        NotificationPreference pref = preferenceMapper.selectById(userId);
        if (pref != null) return pref;
        pref = new NotificationPreference(); pref.setUserId(userId); pref.setEmailEnabled(false); pref.setAssignmentEnabled(true);
        pref.setMentionEnabled(true); pref.setDeadlineEnabled(true); pref.setRiskEnabled(true); pref.setUpdatedAt(LocalDateTime.now());
        try { preferenceMapper.insert(pref); } catch (DuplicateKeyException ignored) { return preferenceMapper.selectById(userId); }
        return pref;
    }

    private boolean enabled(NotificationPreference p, String type) {
        if ("TASK_ASSIGNED".equals(type)) return Boolean.TRUE.equals(p.getAssignmentEnabled());
        if ("COMMENT_MENTIONED".equals(type)) return Boolean.TRUE.equals(p.getMentionEnabled());
        if (Set.of("DUE_SOON", "TASK_OVERDUE", "TASK_BLOCKED").contains(type)) return Boolean.TRUE.equals(p.getDeadlineEnabled());
        if (Set.of("RISK_ASSIGNED", "RISK_HIGH").contains(type)) return Boolean.TRUE.equals(p.getRiskEnabled());
        return false;
    }

    private void enqueueRaw(Long userId, String recipient, String subject, String body, String dedupeKey) {
        EmailOutbox item = new EmailOutbox(); item.setUserId(userId); item.setRecipient(recipient); item.setSubject(subject);
        item.setBody(body == null ? "" : body); item.setStatus("PENDING"); item.setAttempts(0); item.setNextAttemptAt(LocalDateTime.now());
        item.setDedupeKey(dedupeKey); item.setCreatedAt(LocalDateTime.now());
        try { outboxMapper.insert(item); } catch (DuplicateKeyException ignored) { }
    }

    private User requireUser() {
        User user = userMapper.selectById(UserHolder.getUserId()); if (user == null) throw new BusinessException("用户不存在"); return user;
    }
    private NotificationPreferenceVO toVO(User user, NotificationPreference pref) {
        NotificationPreferenceVO vo = new NotificationPreferenceVO(); vo.setEmail(user.getEmail()); vo.setEmailVerifiedAt(user.getEmailVerifiedAt());
        vo.setEmailEnabled(Boolean.TRUE.equals(pref.getEmailEnabled())); vo.setAssignmentEnabled(Boolean.TRUE.equals(pref.getAssignmentEnabled()));
        vo.setMentionEnabled(Boolean.TRUE.equals(pref.getMentionEnabled())); vo.setDeadlineEnabled(Boolean.TRUE.equals(pref.getDeadlineEnabled()));
        vo.setRiskEnabled(Boolean.TRUE.equals(pref.getRiskEnabled())); return vo;
    }
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private String compact(String value) { if (value == null) return "未知错误"; return value.length() > 500 ? value.substring(0, 500) : value; }
}
