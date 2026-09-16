package com.smartpm.controller;
import com.smartpm.common.result.R;
import com.smartpm.dto.*;
import com.smartpm.service.EmailNotificationService;
import com.smartpm.vo.NotificationPreferenceVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/user/notification-preferences") @RequiredArgsConstructor
public class EmailNotificationController {
    private final EmailNotificationService service;
    @GetMapping public R<NotificationPreferenceVO> get() { return R.ok(service.getPreference()); }
    @PutMapping public R<NotificationPreferenceVO> save(@Valid @RequestBody NotificationPreferenceDTO dto) { return R.ok(service.savePreference(dto)); }
    @PostMapping("/verification") public R<Void> requestVerification() { service.requestVerification(); return R.ok(); }
    @PostMapping("/verify") public R<Void> verify(@Valid @RequestBody EmailVerifyDTO dto) { service.verify(dto.getToken()); return R.ok(); }
    @PostMapping("/test") public R<Void> test() { service.sendTest(); return R.ok(); }
}
