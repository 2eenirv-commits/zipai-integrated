package com.onrender.zipai.web;

import com.onrender.zipai.service.ZipaiAuthService;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final ZipaiAuthService auth;
    private final com.onrender.zipai.service.CustomerSupportService support;

    public NotificationController(ZipaiAuthService auth, com.onrender.zipai.service.CustomerSupportService support) {
        this.auth = auth;
        this.support = support;
    }

    @GetMapping
    public Map<String, Object> list(HttpSession session) {
        var user = auth.required(session);
        return support.notifications(user.getId());
    }

    @PatchMapping("/{id}/read")
    public Map<String, Object> read(@PathVariable Long id, HttpSession session) {
        var user = auth.required(session);
        support.readNotification(user.getId(), id);
        return Map.of("success", true);
    }

    @PatchMapping("/read-all")
    public Map<String, Object> readAll(HttpSession session) {
        var user = auth.required(session);
        support.readAllNotifications(user.getId());
        return Map.of("success", true);
    }
}
