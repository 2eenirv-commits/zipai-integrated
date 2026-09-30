package com.onrender.zipai.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinancePolicyUpdateService {
    private final JdbcTemplate jdbc;
    private final String importToken;

    public FinancePolicyUpdateService(
        JdbcTemplate jdbc,
        @Value("${zipai.finance.import-token:}") String importToken
    ) {
        this.jdbc = jdbc;
        this.importToken = importToken == null ? "" : importToken;
    }

    public void verifyImportToken(String supplied) {
        if (importToken.isBlank() || supplied == null || !MessageDigest.isEqual(
            importToken.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "금융정책 Import 인증에 실패했습니다.");
        }
    }

    @Transactional
    public Map<String, Object> importSnapshots(List<Map<String, Object>> snapshots) {
        int checked = 0;
        int baselined = 0;
        int unchanged = 0;
        int detected = 0;
        int missing = 0;
        for (Map<String, Object> snapshot : snapshots) {
            String name = text(snapshot.get("policyName"));
            String sourceName = text(snapshot.get("sourceName"));
            String sourceUrl = text(snapshot.get("sourceUrl"));
            String sourceHash = text(snapshot.get("sourceHash"));
            String excerpt = text(snapshot.get("snapshotExcerpt"));
            if (name.isBlank() || sourceName.isBlank() || sourceUrl.isBlank()
                    || !sourceHash.matches("^[0-9a-f]{64}$") || excerpt.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "금융정책 수집 데이터가 올바르지 않습니다.");
            }
            List<Map<String, Object>> policies = jdbc.queryForList(
                "SELECT id, source_hash FROM finance_policies WHERE name = ? LIMIT 1", name);
            if (policies.isEmpty()) {
                missing++;
                continue;
            }
            checked++;
            long policyId = ((Number) policies.get(0).get("id")).longValue();
            String currentHash = text(policies.get(0).get("source_hash"));
            if (currentHash.isBlank()) {
                jdbc.update("""
                    UPDATE finance_policies
                    SET source_name=?, source_url=?, source_checked_at=NOW(6), source_hash=?, update_status='current'
                    WHERE id=?
                    """, sourceName, sourceUrl, sourceHash, policyId);
                baselined++;
            } else if (currentHash.equals(sourceHash)) {
                jdbc.update("""
                    UPDATE finance_policies
                    SET source_name=?, source_url=?, source_checked_at=NOW(6)
                    WHERE id=?
                    """, sourceName, sourceUrl, policyId);
                unchanged++;
            } else {
                int inserted = jdbc.update("""
                    INSERT IGNORE INTO finance_policy_update_candidates
                      (policy_id, source_name, source_url, source_hash, snapshot_excerpt, status, detected_at)
                    VALUES (?, ?, ?, ?, ?, 'pending', NOW(6))
                    """, policyId, sourceName, sourceUrl, sourceHash, excerpt);
                jdbc.update("""
                    UPDATE finance_policies
                    SET source_name=?, source_url=?, source_checked_at=NOW(6), update_status='review'
                    WHERE id=?
                    """, sourceName, sourceUrl, policyId);
                detected += inserted;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("received", snapshots.size());
        result.put("checked", checked);
        result.put("baselined", baselined);
        result.put("unchanged", unchanged);
        result.put("detected", detected);
        result.put("missingPolicies", missing);
        return result;
    }

    public Map<String, Object> candidates() {
        List<Map<String, Object>> items = jdbc.queryForList("""
            SELECT c.id, c.policy_id AS policyId, p.name AS policyName,
                   p.category, p.target_type AS targetType, p.limit_info AS limitInfo,
                   p.rate_info AS rateInfo, p.description,
                   c.source_name AS sourceName,
                   c.source_url AS sourceUrl, c.source_hash AS sourceHash,
                   c.snapshot_excerpt AS snapshotExcerpt, c.status, c.detected_at AS detectedAt,
                   c.reviewed_at AS reviewedAt
            FROM finance_policy_update_candidates c
            JOIN finance_policies p ON p.id = c.policy_id
            ORDER BY (c.status='pending') DESC, c.detected_at DESC
            """);
        return Map.of("count", items.size(), "items", items);
    }

    @Transactional
    public Map<String, Object> review(long candidateId, long adminUserId, String decision) {
        if (!List.of("approved", "rejected").contains(decision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검토 결과가 올바르지 않습니다.");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT policy_id, source_hash FROM finance_policy_update_candidates
            WHERE id=? AND status='pending'
            """, candidateId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "검토 대상을 찾을 수 없습니다.");
        long policyId = ((Number) rows.get(0).get("policy_id")).longValue();
        String sourceHash = String.valueOf(rows.get(0).get("source_hash"));
        jdbc.update("""
            UPDATE finance_policy_update_candidates
            SET status=?, reviewed_at=NOW(6), reviewed_by=? WHERE id=?
            """, decision, adminUserId, candidateId);
        jdbc.update("""
            UPDATE finance_policies SET source_hash=?, update_status='current', source_checked_at=NOW(6)
            WHERE id=?
            """, sourceHash, policyId);
        return Map.of("success", true, "candidateId", candidateId, "status", decision);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
