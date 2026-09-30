package com.onrender.zipai.web;

import com.onrender.zipai.domain.ZipaiUser;
import com.onrender.zipai.service.SocialLoginService;
import com.onrender.zipai.service.ZipaiAuthService;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class SocialProfileController {
    private final SocialLoginService socialLogin;
    private final ZipaiAuthService auth;

    public SocialProfileController(SocialLoginService socialLogin, ZipaiAuthService auth) {
        this.socialLogin = socialLogin;
        this.auth = auth;
    }

    @PostMapping("/social-profile")
    public Map<String, Object> complete(@RequestBody Map<String, Object> body, HttpSession session) {
        try {
            ZipaiUser user = socialLogin.completeProfile(body, session);
            return Map.of("user", auth.publicUser(user), "profileComplete", true);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage());
        }
    }
}
