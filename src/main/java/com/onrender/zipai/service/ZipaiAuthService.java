package com.onrender.zipai.service;

import com.onrender.zipai.domain.ZipaiUser;
import com.onrender.zipai.repository.ZipaiUserRepository;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ZipaiAuthService {
    private static final String USER_ID = "ZIPAI_USER_ID";
    private static final int MAX_FAILED_LOGIN = 5;

    private final ZipaiUserRepository users;
    private final ZipaiPasswordService passwords;

    public ZipaiAuthService(ZipaiUserRepository users, ZipaiPasswordService passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    public ZipaiUser required(HttpSession session) {
        Object raw = session.getAttribute(USER_ID);
        Long userId = null;
        if (raw instanceof Long value) userId = value;
        else if (raw instanceof Number value) userId = value.longValue();

        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        ZipaiUser user = users.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));

        if (!"active".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용할 수 없는 계정입니다.");
        }
        return user;
    }

    public ZipaiUser current(HttpSession session) {
        try {
            return required(session);
        } catch (ResponseStatusException ignored) {
            return null;
        }
    }

    public void establishSession(ZipaiUser user, HttpSession session) {
        if (user == null || user.getId() == null || !"active".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용할 수 없는 계정입니다.");
        }
        session.setAttribute(USER_ID, user.getId());
    }

    public ZipaiUser admin(HttpSession session) {
        ZipaiUser user = required(session);
        if (!"admin".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
        return user;
    }

    @Transactional
    public ZipaiUser signup(Map<String, Object> body, HttpSession session) {
        String username = text(body, "userId");
        String email = text(body, "email").toLowerCase();
        String phone = text(body, "phone").replaceAll("[^0-9]", "");
        String password = text(body, "password");

        if (!username.matches("^[A-Za-z0-9_가-힣]{4,20}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "아이디는 한글, 영문, 숫자, 밑줄을 사용해 4~20자로 입력해 주세요.");
        }
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 이메일 주소를 입력해 주세요.");
        }
        if (phone.length() < 10 || phone.length() > 11) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 휴대폰 번호를 입력해 주세요.");
        }
        validateNewPassword(password);

        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다.");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        ZipaiUser user = new ZipaiUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwords.encode(password));
        user.setRole("member");
        user.setStatus("active");
        user.setFailedLoginAttempts(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        user = users.save(user);
        session.setAttribute(USER_ID, user.getId());
        return user;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public ZipaiUser login(Map<String, Object> body, HttpSession session) {
        ZipaiUser user = users.findByUsernameIgnoreCase(text(body, "userId"))
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호를 확인해 주세요."));

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.LOCKED,
                "로그인 시도가 제한되었습니다. 잠시 후 다시 시도해 주세요.");
        }

        if (!"active".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "아이디 또는 비밀번호를 확인해 주세요.");
        }

        if (!passwords.matches(text(body, "password"), user.getPasswordHash())) {
            int failed = user.getFailedLoginAttempts() + 1;
            if (failed >= MAX_FAILED_LOGIN) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            } else {
                user.setFailedLoginAttempts(failed);
            }
            user.setUpdatedAt(LocalDateTime.now());
            users.save(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "아이디 또는 비밀번호를 확인해 주세요.");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(LocalDateTime.now());
        users.save(user);

        session.setAttribute(USER_ID, user.getId());
        return user;
    }

    @Transactional
    public ZipaiUser updateProfile(Map<String, Object> body, HttpSession session) {
        ZipaiUser user = required(session);
        String email = text(body, "email").toLowerCase();
        String phone = text(body, "phone").replaceAll("[^0-9]", "");

        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 이메일 주소를 입력해 주세요.");
        }
        if (phone.length() < 10 || phone.length() > 11) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 휴대폰 번호를 입력해 주세요.");
        }
        if (users.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
        }

        user.setEmail(email);
        user.setPhone(phone);
        user.setUpdatedAt(LocalDateTime.now());
        return users.save(user);
    }

    @Transactional
    public void changePassword(Map<String, Object> body, HttpSession session) {
        ZipaiUser user = required(session);
        String current = text(body, "currentPassword");
        String next = text(body, "newPassword");

        if (!passwords.matches(current, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 올바르지 않습니다.");
        }

        validateNewPassword(next);
        user.setPasswordHash(passwords.encode(next));
        user.setUpdatedAt(LocalDateTime.now());
        users.save(user);
    }

    @Transactional
    public void withdraw(Map<String, Object> body, HttpSession session) {
        ZipaiUser user = required(session);
        if (!passwords.matches(text(body, "password"), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다.");
        }

        user.setStatus("deleted");
        user.setDeletedAt(LocalDateTime.now());
        user.setEmail("deleted+" + user.getId() + "@zipai.invalid");
        user.setPhone("");
        user.setUpdatedAt(LocalDateTime.now());
        users.save(user);
        session.invalidate();
    }

    public Map<String, Object> publicUser(ZipaiUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getUsername());
        result.put("userId", user.getId());
        result.put("email", user.getEmail());
        result.put("phone", user.getPhone());
        result.put("role", user.getRole());
        result.put("status", user.getStatus());
        result.put("createdAt", user.getCreatedAt());
        return result;
    }

    private static void validateNewPassword(String password) {
        if (password.length() < 8 || password.length() > 72
                || !password.matches(".*[A-Za-z].*")
                || !password.matches(".*[0-9].*")
                || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "비밀번호는 8~72자이며 영문, 숫자, 특수문자를 각각 포함해야 합니다.");
        }
    }

    private static String text(Map<String, Object> body, String key) {
        return String.valueOf(body.getOrDefault(key, "")).trim();
    }
}
