package com.onrender.zipai.web;

import com.onrender.zipai.domain.ZipaiUser;
import com.onrender.zipai.service.ZipaiAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class ZipaiAuthController {
    private final ZipaiAuthService auth;

    @Value("${zipai.oauth.kakao-configured:false}")
    private boolean kakaoConfigured;
    @Value("${zipai.oauth.naver-configured:false}")
    private boolean naverConfigured;
    @Value("${zipai.oauth.google-configured:false}")
    private boolean googleConfigured;

    public ZipaiAuthController(ZipaiAuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/social-providers")
    public Map<String, Boolean> socialProviders() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        result.put("kakao", kakaoConfigured);
        result.put("naver", naverConfigured);
        result.put("google", googleConfigured);
        return result;
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        if (auth.isGuest(session)) {
            return Map.of("authenticated", true, "user", auth.guestPublicUser());
        }
        ZipaiUser user = auth.current(session);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authenticated", user != null);
        result.put("user", user == null ? null : auth.publicUser(user));
        return result;
    }

    @PostMapping("/guest")
    public Map<String, Object> guest(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        auth.establishGuestSession(session);
        request.changeSessionId();
        return Map.of("user", auth.guestPublicUser());
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> signup(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        ZipaiUser user = auth.signup(body, session);
        request.changeSessionId();
        return Map.of("user", auth.publicUser(user));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        ZipaiUser user = auth.login(body, session);
        request.changeSessionId();
        return Map.of("user", auth.publicUser(user));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return Map.of("success", true);
    }
}
