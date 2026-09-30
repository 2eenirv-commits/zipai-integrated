package com.onrender.zipai.service;

import com.onrender.zipai.domain.ZipaiUser;
import com.onrender.zipai.repository.ZipaiUserRepository;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialLoginService {
    private final JdbcTemplate jdbc;
    private final ZipaiUserRepository users;
    private final ZipaiPasswordService passwords;
    private final ZipaiAuthService auth;

    public SocialLoginService(JdbcTemplate jdbc, ZipaiUserRepository users,
                              ZipaiPasswordService passwords, ZipaiAuthService auth) {
        this.jdbc = jdbc;
        this.users = users;
        this.passwords = passwords;
        this.auth = auth;
    }

    @Transactional
    public ZipaiUser login(OAuth2AuthenticationToken token, HttpSession session) {
        SocialProfile profile = profile(token);
        ZipaiUser user = findLinked(profile.provider(), profile.providerId()).orElse(null);

        if (user == null && profile.emailVerified() && profile.email() != null) {
            user = users.findByEmailIgnoreCase(profile.email()).orElse(null);
        }
        if (user == null) user = createUser(profile);
        if (!"active".equals(user.getStatus())) {
            throw new IllegalStateException("사용할 수 없는 계정입니다.");
        }

        linkIfMissing(user, profile);
        auth.establishSession(user, session);
        return user;
    }

    public boolean requiresProfile(ZipaiUser user) {
        return user == null
            || user.getEmail() == null
            || user.getEmail().endsWith("@social.zipai.invalid")
            || user.getPhone() == null
            || user.getPhone().isBlank();
    }

    @Transactional
    public ZipaiUser completeProfile(Map<String, Object> body, HttpSession session) {
        ZipaiUser user = auth.required(session);
        Integer socialCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM social_accounts WHERE user_id = ?", Integer.class, user.getId());
        if (socialCount == null || socialCount == 0) {
            throw new IllegalArgumentException("소셜 로그인 회원만 사용할 수 있습니다.");
        }

        String username = required(body.get("userId"));
        String email = required(body.get("email")).toLowerCase();
        String phone = required(body.get("phone")).replaceAll("[^0-9]", "");
        if (!username.matches("^[A-Za-z0-9_가-힣]{4,20}$")) {
            throw new IllegalArgumentException("아이디는 한글, 영문, 숫자, 밑줄을 사용해 4~20자로 입력해 주세요.");
        }
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("올바른 이메일 주소를 입력해 주세요.");
        }
        if (phone.length() < 10 || phone.length() > 11) {
            throw new IllegalArgumentException("올바른 휴대폰 번호를 입력해 주세요.");
        }
        if (users.existsByUsernameIgnoreCaseAndIdNot(username, user.getId())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (users.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(phone);
        user.setUpdatedAt(LocalDateTime.now());
        return users.save(user);
    }

    private Optional<ZipaiUser> findLinked(String provider, String providerId) {
        return jdbc.query("""
                SELECT u.* FROM users u
                JOIN social_accounts s ON s.user_id = u.id
                WHERE s.provider = ? AND s.provider_user_id = ?
                """, (rs, rowNum) -> {
                    ZipaiUser user = new ZipaiUser();
                    user.setId(rs.getLong("id"));
                    user.setUsername(rs.getString("username"));
                    user.setEmail(rs.getString("email"));
                    user.setPhone(rs.getString("phone"));
                    user.setPasswordHash(rs.getString("password_hash"));
                    user.setRole(rs.getString("role"));
                    user.setStatus(rs.getString("status"));
                    user.setFailedLoginAttempts(rs.getInt("failed_login_attempts"));
                    user.setLockedUntil(rs.getTimestamp("locked_until") == null ? null : rs.getTimestamp("locked_until").toLocalDateTime());
                    user.setEmailVerifiedAt(rs.getTimestamp("email_verified_at") == null ? null : rs.getTimestamp("email_verified_at").toLocalDateTime());
                    user.setPhoneVerifiedAt(rs.getTimestamp("phone_verified_at") == null ? null : rs.getTimestamp("phone_verified_at").toLocalDateTime());
                    user.setDeletedAt(rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toLocalDateTime());
                    user.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    user.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                    return user;
                }, provider, providerId).stream().findFirst();
    }

    private ZipaiUser createUser(SocialProfile profile) {
        LocalDateTime now = LocalDateTime.now();
        String email = usableEmail(profile);
        ZipaiUser user = new ZipaiUser();
        user.setUsername(uniqueUsername(profile.provider(), profile.providerId()));
        user.setEmail(email);
        user.setPhone("");
        user.setPasswordHash(passwords.encode(UUID.randomUUID() + "!Aa1"));
        user.setRole("member");
        user.setStatus("active");
        user.setFailedLoginAttempts(0);
        if (profile.emailVerified() && profile.email() != null) user.setEmailVerifiedAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return users.save(user);
    }

    private String usableEmail(SocialProfile profile) {
        if (profile.email() != null && !users.existsByEmailIgnoreCase(profile.email())) return profile.email();
        return profile.provider() + "+" + profile.providerId() + "@social.zipai.invalid";
    }

    private String uniqueUsername(String provider, String providerId) {
        String clean = providerId.replaceAll("[^A-Za-z0-9]", "");
        if (clean.length() > 11) clean = clean.substring(clean.length() - 11);
        String base = (provider + "_" + clean);
        if (base.length() > 20) base = base.substring(0, 20);
        String candidate = base;
        int suffix = 1;
        while (users.existsByUsernameIgnoreCase(candidate)) {
            String tail = String.valueOf(suffix++);
            candidate = base.substring(0, Math.min(base.length(), 20 - tail.length())) + tail;
        }
        return candidate;
    }

    private void linkIfMissing(ZipaiUser user, SocialProfile profile) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
            INSERT INTO social_accounts
                (user_id, provider, provider_user_id, email, display_name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                email = COALESCE(VALUES(email), email),
                display_name = COALESCE(VALUES(display_name), display_name),
                updated_at = VALUES(updated_at)
            """, user.getId(), profile.provider(), profile.providerId(), profile.email(),
            profile.displayName(), now, now);
    }

    @SuppressWarnings("unchecked")
    private SocialProfile profile(OAuth2AuthenticationToken token) {
        String provider = token.getAuthorizedClientRegistrationId().toLowerCase();
        Map<String, Object> a = token.getPrincipal().getAttributes();
        if ("google".equals(provider)) {
            return new SocialProfile(provider, required(a.get("sub")), text(a.get("email")),
                text(a.get("name")), Boolean.TRUE.equals(a.get("email_verified")));
        }
        if ("kakao".equals(provider)) {
            Map<String, Object> account = map(a.get("kakao_account"));
            Map<String, Object> kakaoProfile = map(account.get("profile"));
            Map<String, Object> properties = map(a.get("properties"));
            boolean verified = Boolean.TRUE.equals(account.get("is_email_valid"))
                && Boolean.TRUE.equals(account.get("is_email_verified"));
            return new SocialProfile(provider, required(a.get("id")), text(account.get("email")),
                first(text(kakaoProfile.get("nickname")), text(properties.get("nickname"))), verified);
        }
        if ("naver".equals(provider)) {
            Map<String, Object> response = map(a.get("response"));
            return new SocialProfile(provider, required(response.get("id")), text(response.get("email")),
                first(text(response.get("name")), text(response.get("nickname"))), response.get("email") != null);
        }
        throw new IllegalArgumentException("지원하지 않는 소셜 로그인입니다.");
    }

    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }
    private static String required(Object value) {
        String result = String.valueOf(value == null ? "" : value).trim();
        if (result.isEmpty()) throw new IllegalArgumentException("소셜 계정 식별자를 받지 못했습니다.");
        return result;
    }
    private static String text(Object value) {
        String result = String.valueOf(value == null ? "" : value).trim();
        return result.isEmpty() ? null : result;
    }
    private static String first(String left, String right) { return left != null ? left : right; }

    private record SocialProfile(String provider, String providerId, String email,
                                 String displayName, boolean emailVerified) {}
}
