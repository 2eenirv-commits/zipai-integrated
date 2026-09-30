package com.onrender.zipai.web;

import com.onrender.zipai.domain.ZipaiUser;
import com.onrender.zipai.service.AdminOperationsService;
import com.onrender.zipai.service.ZipaiAuthService;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminOperationsController {
    private final AdminOperationsService operations;
    private final ZipaiAuthService auth;

    public AdminOperationsController(AdminOperationsService operations, ZipaiAuthService auth) {
        this.operations = operations;
        this.auth = auth;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(HttpSession session) {
        auth.admin(session);
        return operations.summary();
    }

    @GetMapping("/properties")
    public Map<String, Object> properties(HttpSession session) {
        auth.admin(session);
        return Map.of("items", operations.properties());
    }

    @PatchMapping("/properties/{id}/status")
    public Map<String, Object> propertyStatus(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                               HttpSession session) {
        ZipaiUser admin = auth.admin(session);
        operations.changePropertyStatus(admin.getId(), id, String.valueOf(body.getOrDefault("status", "")));
        return Map.of("success", true);
    }

    @GetMapping("/visits")
    public Map<String, Object> visits(HttpSession session) {
        auth.admin(session);
        return Map.of("items", operations.visits());
    }

    @PatchMapping("/visits/{id}/status")
    public Map<String, Object> visitStatus(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                            HttpSession session) {
        ZipaiUser admin = auth.admin(session);
        operations.changeVisitStatus(admin.getId(), id, String.valueOf(body.getOrDefault("status", "")));
        return Map.of("success", true);
    }

    @GetMapping("/community/posts")
    public Map<String, Object> posts(HttpSession session) {
        auth.admin(session);
        return Map.of("items", operations.posts());
    }

    @DeleteMapping("/community/posts/{id}")
    public Map<String, Object> deletePost(@PathVariable Long id, HttpSession session) {
        ZipaiUser admin = auth.admin(session);
        operations.deletePost(admin.getId(), id);
        return Map.of("success", true);
    }

    @GetMapping("/audit")
    public Map<String, Object> audit(HttpSession session) {
        auth.admin(session);
        return Map.of("items", operations.auditLogs());
    }
}
