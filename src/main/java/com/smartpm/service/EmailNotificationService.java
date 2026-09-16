package com.smartpm.service;
import com.smartpm.dto.NotificationPreferenceDTO;
import com.smartpm.entity.Notification;
import com.smartpm.vo.NotificationPreferenceVO;
public interface EmailNotificationService {
    NotificationPreferenceVO getPreference();
    NotificationPreferenceVO savePreference(NotificationPreferenceDTO dto);
    void requestVerification();
    void verify(String token);
    void sendTest();
    void enqueue(Notification notification);
    void deliverPending();
}
