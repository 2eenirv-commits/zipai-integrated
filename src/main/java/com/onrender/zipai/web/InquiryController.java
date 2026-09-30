package com.onrender.zipai.web;

import com.onrender.zipai.domain.ZipaiUser;
import com.onrender.zipai.service.CustomerSupportService;
import com.onrender.zipai.service.ZipaiAuthService;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inquiries")
public class InquiryController {
    private final CustomerSupportService support;
    private final ZipaiAuthService auth;

    public InquiryController(CustomerSupportService support, ZipaiAuthService auth) {
        this.support = support;
        this.auth = auth;
    }

    @GetMapping
    public Map<String, Object> list(HttpSession session) {
        ZipaiUser user = auth.required(session);
        return Map.of("items", support.inquiriesForUser(user.getId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody Map<String, Object> body, HttpSession session) {
        ZipaiUser user = auth.required(session);
        return support.createInquiry(user.getId(), body);
    }
}
