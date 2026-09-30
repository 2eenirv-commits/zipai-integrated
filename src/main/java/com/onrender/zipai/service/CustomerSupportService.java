package com.onrender.zipai.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerSupportService {
    private static final Set<String> CATEGORIES = Set.of("매물 이용", "계약 안전", "로그인·계정", "서비스 오류", "기타");
    private static final Set<String> STATUSES = Set.of("received", "in_progress", "answered");

    private final JdbcTemplate jdbc;

    public CustomerSupportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> inquiriesForUser(Long userId) {
        return jdbc.query("""
            SELECT id, category, email, title, message, status, answer, answered_at, created_at, updated_at
              FROM customer_inquiry
             WHERE user_id = ?
             ORDER BY created_at DESC, id DESC
            """, this::inquiryRow, userId);
    }

    @Transactional
    public Map<String, Object> createInquiry(Long userId, Map<String, Object> body) {
        String category = text(body, "category");
        String email = text(body, "email").toLowerCase();
        String title = text(body, "title");
        String message = text(body, "message");
        if (!CATEGORIES.contains(category)) {
            throw badRequest("문의 유형을 확인해 주세요.");
        }
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length() > 254) {
            throw badRequest("올바른 이메일 주소를 입력해 주세요.");
        }
        if (title.length() < 2 || title.length() > 60) {
            throw badRequest("문의 제목은 2~60자로 입력해 주세요.");
        }
        if (message.length() < 2 || message.length() > 1000) {
            throw badRequest("문의 내용은 2~1000자로 입력해 주세요.");
        }
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
            INSERT INTO customer_inquiry
                (user_id, category, email, title, message, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'received', ?, ?)
            """, userId, category, email, title, message, now, now);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return Map.of("success", true, "id", id == null ? 0L : id);
    }

    public List<Map<String, Object>> allInquiries() {
        return jdbc.query("""
            SELECT i.id, i.user_id, u.username, i.category, i.email, i.title, i.message,
                   i.status, i.answer, i.answered_at, i.created_at, i.updated_at
              FROM customer_inquiry i
              JOIN users u ON u.id = i.user_id
             ORDER BY i.created_at DESC, i.id DESC
            """, this::adminInquiryRow);
    }

    @Transactional
    public void answerInquiry(Long inquiryId, Map<String, Object> body) {
        String status = text(body, "status");
        String answer = text(body, "answer");
        if (!STATUSES.contains(status)) throw badRequest("처리 상태를 확인해 주세요.");
        if (answer.length() > 1500) throw badRequest("답변은 1500자 이하로 입력해 주세요.");
        if ("answered".equals(status) && answer.isBlank()) {
            throw badRequest("답변 완료 시 답변 내용을 입력해 주세요.");
        }

        Map<String, Object> previous = jdbc.query("""
            SELECT user_id, title, status, COALESCE(answer, '') AS answer
              FROM customer_inquiry WHERE id = ?
            """, rs -> {
                if (!rs.next()) return null;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("userId", rs.getLong("user_id"));
                row.put("title", rs.getString("title"));
                row.put("status", rs.getString("status"));
                row.put("answer", rs.getString("answer"));
                return row;
            }, inquiryId);
        if (previous == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime answeredAt = "answered".equals(status) ? now : null;
        jdbc.update("""
            UPDATE customer_inquiry
               SET status = ?, answer = ?, answered_at = ?, updated_at = ?
             WHERE id = ?
            """, status, answer.isBlank() ? null : answer, answeredAt, now, inquiryId);

        boolean newAnswer = "answered".equals(status)
            && (!"answered".equals(previous.get("status")) || !answer.equals(previous.get("answer")));
        if (newAnswer) {
            jdbc.update("""
                INSERT INTO user_notification
                    (user_id, notification_type, title, message, target_url, is_read, created_at)
                VALUES (?, 'inquiry_answer', ?, ?, '/board/customer-center#inquiry-history', FALSE, ?)
                """, previous.get("userId"), "1:1 문의 답변이 등록되었습니다",
                "‘" + previous.get("title") + "’ 문의의 답변을 확인해 주세요.", now);
        }
    }

    public Map<String, Object> notifications(Long userId) {
        List<Map<String, Object>> items = jdbc.query("""
            SELECT id, notification_type, title, message, target_url, is_read, created_at, read_at
              FROM user_notification
             WHERE user_id = ?
             ORDER BY created_at DESC, id DESC
             LIMIT 100
            """, this::notificationRow, userId);
        long unread = items.stream().filter(item -> !Boolean.TRUE.equals(item.get("read"))).count();
        return Map.of("unreadCount", unread, "items", items);
    }

    public void readNotification(Long userId, Long notificationId) {
        int changed = jdbc.update("""
            UPDATE user_notification SET is_read = TRUE, read_at = COALESCE(read_at, ?)
             WHERE id = ? AND user_id = ?
            """, LocalDateTime.now(), notificationId, userId);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다.");
    }

    public void readAllNotifications(Long userId) {
        jdbc.update("""
            UPDATE user_notification SET is_read = TRUE, read_at = COALESCE(read_at, ?)
             WHERE user_id = ? AND is_read = FALSE
            """, LocalDateTime.now(), userId);
    }

    private Map<String, Object> inquiryRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("category", rs.getString("category"));
        row.put("email", rs.getString("email"));
        row.put("title", rs.getString("title"));
        row.put("message", rs.getString("message"));
        row.put("status", rs.getString("status"));
        row.put("answer", rs.getString("answer"));
        row.put("answeredAt", localDateTime(rs, "answered_at"));
        row.put("createdAt", localDateTime(rs, "created_at"));
        row.put("updatedAt", localDateTime(rs, "updated_at"));
        return row;
    }

    private Map<String, Object> adminInquiryRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = inquiryRow(rs, rowNum);
        row.put("userId", rs.getLong("user_id"));
        row.put("username", rs.getString("username"));
        return row;
    }

    private Map<String, Object> notificationRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("type", rs.getString("notification_type"));
        row.put("title", rs.getString("title"));
        row.put("message", rs.getString("message"));
        row.put("targetUrl", rs.getString("target_url"));
        row.put("read", rs.getBoolean("is_read"));
        row.put("createdAt", localDateTime(rs, "created_at"));
        row.put("readAt", localDateTime(rs, "read_at"));
        return row;
    }

    private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
    }

    private static String text(Map<String, Object> body, String key) {
        return String.valueOf(body.getOrDefault(key, "")).trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
