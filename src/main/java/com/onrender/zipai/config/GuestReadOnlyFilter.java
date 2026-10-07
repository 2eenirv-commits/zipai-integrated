package com.onrender.zipai.config;

import tools.jackson.databind.ObjectMapper;
import com.onrender.zipai.service.ZipaiAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class GuestReadOnlyFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> GUEST_AUTH_ENDPOINTS = Set.of(
        "/api/auth/guest", "/api/auth/logout"
    );
    private static final Set<String> READ_ONLY_POST_ENDPOINTS = Set.of(
        "/api/lifestyle/recommend",
        "/api/lifestyle/recommend/ml",
        "/api/happy-housing/diagnose",
        "/api/finance/calculate",
        "/api/chat"
    );

    private final ObjectMapper objectMapper;

    public GuestReadOnlyFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        boolean guest = session != null
            && Boolean.TRUE.equals(session.getAttribute(ZipaiAuthService.GUEST_SESSION));

        if (!guest) {
            filterChain.doFilter(request, response);
            return;
        }

        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            "portfolio_guest",
            null,
            AuthorityUtils.createAuthorityList(ZipaiAuthService.GUEST_ROLE)
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String method = request.getMethod();
        String path = request.getRequestURI();
        boolean allowed = SAFE_METHODS.contains(method)
            || GUEST_AUTH_ENDPOINTS.contains(path)
            || ("POST".equals(method) && READ_ONLY_POST_ENDPOINTS.contains(path));

        if (allowed) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
            "status", HttpServletResponse.SC_FORBIDDEN,
            "message", "게스트 모드에서는 조회만 가능합니다."
        ));
    }
}
