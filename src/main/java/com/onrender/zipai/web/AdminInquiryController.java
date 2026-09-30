package com.onrender.zipai.web;

import com.onrender.zipai.service.CustomerSupportService;
import com.onrender.zipai.service.AdminOperationsService;
import com.onrender.zipai.service.ZipaiAuthService;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/inquiries")
public class AdminInquiryController {
    private final CustomerSupportService support;
    private final ZipaiAuthService auth;
    private final AdminOperationsService operations;

    public AdminInquiryController(CustomerSupportService support, ZipaiAuthService auth,
                                  AdminOperationsService operations) {
        this.support = support;
        this.auth = auth;
        this.operations = operations;
    }

    @GetMapping
    public Map<String, Object> list(HttpSession session) {
        auth.admin(session);
        return Map.of("items", support.allInquiries());
    }

    @PatchMapping("/{id}/answer")
    public Map<String, Object> answer(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpSession session) {
        var admin = auth.admin(session);
        support.answerInquiry(id, body);
        operations.audit(admin.getId(), "INQUIRY_UPDATED", "inquiry", String.valueOf(id),
            "status=" + String.valueOf(body.getOrDefault("status", "")));
        return Map.of("success", true);
    }
}
