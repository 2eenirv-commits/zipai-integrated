package com.onrender.zipai.service;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FraudDiagnosisService {
    private static final int QUESTION_COUNT = 12;
    private final JdbcTemplate jdbc;

    public FraudDiagnosisService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> save(long userId, Map<String, Object> body) {
        String clientRef = text(body.get("clientRef"));
        if (clientRef.isBlank() || clientRef.length() > 64) {
            throw badRequest("진단 식별값이 올바르지 않습니다.");
        }

        int safe = integer(body.get("safeCount"), "안전 항목");
        int caution = integer(body.get("cautionCount"), "주의 항목");
        int danger = integer(body.get("dangerCount"), "위험 항목");
        int unanswered = integer(body.get("unansweredCount"), "미응답 항목");
        if (safe < 0 || caution < 0 || danger < 0 || unanswered < 0
                || safe + caution + danger + unanswered != QUESTION_COUNT) {
            throw badRequest("체크리스트 항목 수가 올바르지 않습니다.");
        }

        int checklistScore = (int) Math.round(
            ((safe * 1.0) + (caution * 0.5) + (unanswered * 0.25)) / QUESTION_COUNT * 100
        );
        Double ratio = decimal(body.get("jeonseRatio"));
        if (ratio != null && (!Double.isFinite(ratio) || ratio < 0 || ratio > 1000)) {
            throw badRequest("전세가율이 올바르지 않습니다.");
        }
        final int finalScore;
        if (ratio != null) {
            int ratioScore = ratio < 70 ? 100 : ratio < 80 ? 60 : 20;
            finalScore = (int) Math.round(checklistScore * 0.8 + ratioScore * 0.2);
        } else {
            finalScore = checklistScore;
        }
        String riskLevel = finalScore >= 80 ? "safe" : finalScore >= 55 ? "caution" : "danger";

        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO fraud_diagnoses
                  (user_id, client_ref, checklist_score, final_score, safe_count, caution_count,
                   danger_count, unanswered_count, jeonse_ratio, risk_level, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6))
                ON DUPLICATE KEY UPDATE
                  checklist_score=VALUES(checklist_score), final_score=VALUES(final_score),
                  safe_count=VALUES(safe_count), caution_count=VALUES(caution_count),
                  danger_count=VALUES(danger_count), unanswered_count=VALUES(unanswered_count),
                  jeonse_ratio=VALUES(jeonse_ratio), risk_level=VALUES(risk_level), updated_at=NOW(6)
                """);
            statement.setLong(1, userId);
            statement.setString(2, clientRef);
            statement.setInt(3, checklistScore);
            statement.setInt(4, finalScore);
            statement.setInt(5, safe);
            statement.setInt(6, caution);
            statement.setInt(7, danger);
            statement.setInt(8, unanswered);
            if (ratio == null) statement.setNull(9, Types.DECIMAL);
            else statement.setBigDecimal(9, BigDecimal.valueOf(ratio));
            statement.setString(10, riskLevel);
            return statement;
        });
        return latest(userId);
    }

    public Map<String, Object> latest(long userId) {
        List<Map<String, Object>> rows = jdbc.query("""
            SELECT id, client_ref, checklist_score, final_score, safe_count, caution_count, danger_count,
                   unanswered_count, jeonse_ratio, risk_level, updated_at
            FROM fraud_diagnoses
            WHERE user_id = ?
            ORDER BY updated_at DESC, id DESC
            LIMIT 1
            """, (rs, rowNum) -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", rs.getLong("id"));
                result.put("clientRef", rs.getString("client_ref"));
                result.put("checklistScore", rs.getInt("checklist_score"));
                result.put("finalScore", rs.getInt("final_score"));
                result.put("safeCount", rs.getInt("safe_count"));
                result.put("cautionCount", rs.getInt("caution_count"));
                result.put("dangerCount", rs.getInt("danger_count"));
                result.put("unansweredCount", rs.getInt("unanswered_count"));
                BigDecimal ratio = rs.getBigDecimal("jeonse_ratio");
                result.put("jeonseRatio", ratio == null ? null : ratio.doubleValue());
                result.put("riskLevel", rs.getString("risk_level"));
                LocalDateTime updatedAt = rs.getTimestamp("updated_at").toLocalDateTime();
                result.put("updatedAt", updatedAt);
                return result;
            }, userId);
        if (rows.isEmpty()) return Map.of("available", false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", true);
        result.put("diagnosis", rows.get(0));
        return result;
    }

    private static int integer(Object value, String label) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException error) {
            throw badRequest(label + " 값이 올바르지 않습니다.");
        }
    }

    private static Double decimal(Object value) {
        String text = text(value);
        if (text.isBlank()) return null;
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException error) {
            throw badRequest("전세가율이 올바르지 않습니다.");
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
