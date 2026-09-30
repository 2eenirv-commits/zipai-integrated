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
public class AdminOperationsService {
    private static final Set<String> PROPERTY_STATUSES = Set.of("approved", "rejected", "closed");
    private static final Set<String> VISIT_STATUSES = Set.of(
        "pending", "reschedule_requested", "approved", "rejected", "completed", "no_show",
        "cancelled_by_user", "cancelled_by_admin"
    );

    private final JdbcTemplate jdbc;

    public AdminOperationsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("newMembers", count("SELECT COUNT(*) FROM users WHERE status='active' AND DATE(created_at)=CURRENT_DATE"));
        result.put("activeMembers", count("SELECT COUNT(*) FROM users WHERE status='active'"));
        result.put("newInquiries", count("SELECT COUNT(*) FROM customer_inquiry WHERE status='received'"));
        result.put("unansweredInquiries", count("SELECT COUNT(*) FROM customer_inquiry WHERE status<>'answered'"));
        result.put("pendingProperties", count("SELECT COUNT(*) FROM property_listing WHERE source_type='USER' AND status IN ('pending','review','received')"));
        result.put("pendingVisits", count("SELECT COUNT(*) FROM room_visit WHERE status IN ('pending','reschedule_requested')"));
        return result;
    }

    public List<Map<String, Object>> properties() {
        return jdbc.query("""
            SELECT p.property_id, p.owner_user_id, u.username, p.title, p.address, p.deal_type,
                   p.building_type, p.status, p.created_at, p.updated_at
              FROM property_listing p
              LEFT JOIN users u ON u.id = p.owner_user_id
             WHERE p.source_type = 'USER'
             ORDER BY p.created_at DESC, p.property_id DESC
             LIMIT 300
            """, this::propertyRow);
    }

    @Transactional
    public void changePropertyStatus(Long adminId, Long propertyId, String requestedStatus) {
        String requested = requestedStatus == null ? "" : requestedStatus.trim().toLowerCase();
        if (!PROPERTY_STATUSES.contains(requested)) badRequest("매물 처리 상태를 확인해 주세요.");
        String storedStatus = "approved".equals(requested) ? "active" : requested;
        Map<String, Object> property = jdbc.query("""
            SELECT owner_user_id, title FROM property_listing
             WHERE property_id=? AND source_type='USER'
            """, rs -> rs.next() ? Map.of(
                "ownerId", rs.getLong("owner_user_id"),
                "title", rs.getString("title")
            ) : null, propertyId);
        if (property == null) notFound("등록 매물을 찾을 수 없습니다.");
        jdbc.update("UPDATE property_listing SET status=?, updated_at=? WHERE property_id=?",
            storedStatus, LocalDateTime.now(), propertyId);
        String label = "approved".equals(requested) ? "승인" : "rejected".equals(requested) ? "거절" : "종료";
        Long ownerId = ((Number) property.get("ownerId")).longValue();
        if (ownerId > 0) {
            notify(ownerId, "property_status", "등록 매물 상태가 변경되었습니다",
                "‘" + property.get("title") + "’ 매물이 " + label + " 처리되었습니다.", "/properties/register");
        }
        audit(adminId, "PROPERTY_STATUS_CHANGED", "property", String.valueOf(propertyId), "status=" + requested);
    }

    public List<Map<String, Object>> visits() {
        return jdbc.query("""
            SELECT visit_id, room_id, title, visit_date, visit_time, phone, question, status, created_at, updated_at
              FROM room_visit ORDER BY created_at DESC, visit_id DESC LIMIT 300
            """, this::visitRow);
    }

    @Transactional
    public void changeVisitStatus(Long adminId, Long visitId, String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase();
        if (!VISIT_STATUSES.contains(normalized)) badRequest("방문 예약 상태를 확인해 주세요.");
        int changed = jdbc.update("UPDATE room_visit SET status=?, updated_at=? WHERE visit_id=?",
            normalized, LocalDateTime.now(), visitId);
        if (changed == 0) notFound("방문 예약을 찾을 수 없습니다.");
        audit(adminId, "VISIT_STATUS_CHANGED", "visit", String.valueOf(visitId), "status=" + normalized);
    }

    public List<Map<String, Object>> posts() {
        return jdbc.query("""
            SELECT p.id, p.author_id, u.username, p.category, p.title, p.area, p.views, p.created_at
              FROM community_posts p
              JOIN users u ON u.id = p.author_id
             ORDER BY p.created_at DESC, p.id DESC LIMIT 300
            """, this::postRow);
    }

    @Transactional
    public void deletePost(Long adminId, Long postId) {
        Map<String, Object> post = jdbc.query("SELECT author_id, title FROM community_posts WHERE id=?",
            rs -> rs.next() ? Map.of("authorId", rs.getLong("author_id"), "title", rs.getString("title")) : null,
            postId);
        if (post == null) notFound("게시글을 찾을 수 없습니다.");
        jdbc.update("DELETE FROM community_posts WHERE id=?", postId);
        notify(((Number) post.get("authorId")).longValue(), "community_moderation", "게시글이 운영 삭제되었습니다",
            "‘" + post.get("title") + "’ 게시글이 운영 정책에 따라 삭제되었습니다.", "/board/community");
        audit(adminId, "COMMUNITY_POST_DELETED", "community_post", String.valueOf(postId),
            "title=" + truncate(String.valueOf(post.get("title")), 400));
    }

    public List<Map<String, Object>> auditLogs() {
        return jdbc.query("""
            SELECT a.id, a.action, a.target_type, a.target_id, a.details, a.created_at,
                   COALESCE(u.username, '삭제된 관리자') AS admin_username
              FROM admin_audit_log a
              LEFT JOIN users u ON u.id = a.admin_user_id
             ORDER BY a.created_at DESC, a.id DESC LIMIT 100
            """, this::auditRow);
    }

    public void audit(Long adminId, String action, String targetType, String targetId, String details) {
        jdbc.update("""
            INSERT INTO admin_audit_log(admin_user_id, action, target_type, target_id, details, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """, adminId, action, targetType, targetId, truncate(details, 500), LocalDateTime.now());
    }

    private void notify(Long userId, String type, String title, String message, String targetUrl) {
        jdbc.update("""
            INSERT INTO user_notification
                (user_id, notification_type, title, message, target_url, is_read, created_at)
            VALUES (?, ?, ?, ?, ?, FALSE, ?)
            """, userId, type, title, message, targetUrl, LocalDateTime.now());
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private Map<String, Object> propertyRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("property_id"));
        row.put("ownerId", rs.getObject("owner_user_id"));
        row.put("owner", rs.getString("username"));
        row.put("title", rs.getString("title"));
        row.put("address", rs.getString("address"));
        row.put("dealType", rs.getString("deal_type"));
        row.put("buildingType", rs.getString("building_type"));
        row.put("status", rs.getString("status"));
        row.put("createdAt", localDateTime(rs, "created_at"));
        row.put("updatedAt", localDateTime(rs, "updated_at"));
        return row;
    }

    private Map<String, Object> visitRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("visit_id"));
        row.put("roomId", rs.getString("room_id"));
        row.put("title", rs.getString("title"));
        row.put("date", rs.getDate("visit_date").toLocalDate());
        row.put("time", rs.getTime("visit_time").toLocalTime());
        row.put("phone", rs.getString("phone"));
        row.put("question", rs.getString("question"));
        row.put("status", rs.getString("status"));
        row.put("createdAt", localDateTime(rs, "created_at"));
        row.put("updatedAt", localDateTime(rs, "updated_at"));
        return row;
    }

    private Map<String, Object> postRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("authorId", rs.getLong("author_id"));
        row.put("username", rs.getString("username"));
        row.put("category", rs.getString("category"));
        row.put("title", rs.getString("title"));
        row.put("area", rs.getString("area"));
        row.put("views", rs.getLong("views"));
        row.put("createdAt", localDateTime(rs, "created_at"));
        return row;
    }

    private Map<String, Object> auditRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("admin", rs.getString("admin_username"));
        row.put("action", rs.getString("action"));
        row.put("targetType", rs.getString("target_type"));
        row.put("targetId", rs.getString("target_id"));
        row.put("details", rs.getString("details"));
        row.put("createdAt", localDateTime(rs, "created_at"));
        return row;
    }

    private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static void badRequest(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static void notFound(String message) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
