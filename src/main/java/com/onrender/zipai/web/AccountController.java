package com.onrender.zipai.web;

import com.onrender.zipai.domain.ZipaiUser;
import com.onrender.zipai.service.ZipaiAuthService;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final ZipaiAuthService auth;

    public AccountController(ZipaiAuthService auth) {
        this.auth = auth;
    }

    @GetMapping
    public Map<String, Object> account(HttpSession session) {
        ZipaiUser user = auth.required(session);
        return Map.of("user", auth.publicUser(user));
    }

    @PutMapping("/profile")
    public Map<String, Object> updateProfile(@RequestBody Map<String, Object> body, HttpSession session) {
        return Map.of("user", auth.publicUser(auth.updateProfile(body, session)));
    }

    @PutMapping("/password")
    public Map<String, Object> changePassword(@RequestBody Map<String, Object> body, HttpSession session) {
        auth.changePassword(body, session);
        return Map.of("success", true);
    }

    @DeleteMapping
    public Map<String, Object> withdraw(@RequestBody Map<String, Object> body, HttpSession session) {
        auth.withdraw(body, session);
        return Map.of("success", true);
    }
}
