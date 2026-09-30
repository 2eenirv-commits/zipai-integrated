package com.onrender.zipai.config;

import com.onrender.zipai.service.SocialLoginService;
import com.onrender.zipai.domain.ZipaiUser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class SocialLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final SocialLoginService socialLogin;

    public SocialLoginSuccessHandler(SocialLoginService socialLogin) {
        this.socialLogin = socialLogin;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth)) {
            response.sendRedirect("/member/login?oauthError=unsupported");
            return;
        }
        try {
            ZipaiUser user = socialLogin.login(oauth, request.getSession(true));
            request.changeSessionId();
            response.sendRedirect(socialLogin.requiresProfile(user) ? "/member/social-profile" : "/");
        } catch (RuntimeException error) {
            response.sendRedirect("/member/login?oauthError=failed");
        }
    }
}
