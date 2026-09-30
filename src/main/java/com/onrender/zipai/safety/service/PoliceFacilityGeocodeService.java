package com.onrender.zipai.safety.service;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public class PoliceFacilityGeocodeService {
    private final JdbcTemplate jdbc;
    private final SafetyCoordinateGeocoder geocoder;

    public PoliceFacilityGeocodeService(JdbcTemplate jdbc, SafetyCoordinateGeocoder geocoder) {
        this.jdbc = jdbc;
        this.geocoder = geocoder;
    }

    public BatchResult geocodeMissingCoordinates(int limit, long delayMillis) {
        return geocodeMissingCoordinates(limit, delayMillis, false);
    }

    public BatchResult geocodeMissingCoordinates(int limit, long delayMillis, boolean retryFailed) {
        List<Target> targets = targets(limit, retryFailed);
        int success = 0;
        int failed = 0;
        int skipped = 0;
        int apiCalls = 0;
        for (int index = 0; index < targets.size(); index++) {
            Target target = targets.get(index);
            String prefix = "[" + (index + 1) + "/" + targets.size() + "] ";
            if (target.address() == null || target.address().isBlank()) {
                markFailed(target.id(), "blank address");
                failed++;
                System.out.println(prefix + "failed - " + target.name() + " (blank address)");
                continue;
            }
            try {
                apiCalls++;
                Optional<SafetyCoordinateGeocoder.Coordinate> coordinate = geocoder.geocode(target.address().trim());
                if (coordinate.isEmpty()) {
                    markFailed(target.id(), "no geocoding result");
                    failed++;
                    System.out.println(prefix + "failed - " + target.name() + " (" + target.address() + ")");
                } else {
                    int updated = updateCoordinate(target.id(), coordinate.get());
                    if (updated > 0) {
                        success++;
                        System.out.println(prefix + "success - " + target.name());
                    } else {
                        skipped++;
                        System.out.println(prefix + "skip - " + target.name() + " (already updated)");
                    }
                }
            } catch (Exception error) {
                markFailed(target.id(), errorSummary(error));
                failed++;
                System.out.println(prefix + "failed - " + target.name() + " (" + target.address() + ")");
            }
            sleep(delayMillis);
        }
        return new BatchResult(
            targets.size(), success, failed, skipped, apiCalls,
            remainingTargets(), failedTargets(), nullCoordinateTargets()
        );
    }

    private List<Target> targets(int limit, boolean retryFailed) {
        return jdbc.query("""
            SELECT id, name, address, facility_type
            FROM safe_infrastructure
            WHERE facility_type IN ('POLICE_BOX', 'POLICE_STATION')
              AND (latitude IS NULL OR longitude IS NULL)
              AND geocode_status = ?
            ORDER BY id
            LIMIT ?
            """, (rs, rowNum) -> new Target(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("address"),
            rs.getString("facility_type")
        ), retryFailed ? "FAILED" : "NOT_ATTEMPTED", Math.max(1, limit));
    }

    private int updateCoordinate(long id, SafetyCoordinateGeocoder.Coordinate coordinate) {
        return jdbc.update("""
            UPDATE safe_infrastructure
            SET latitude = ?, longitude = ?,
                geocode_status = 'VERIFIED',
                geocode_attempts = geocode_attempts + 1,
                geocode_last_attempt_at = CURRENT_TIMESTAMP(6),
                geocode_error = NULL,
                updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = ?
              AND facility_type IN ('POLICE_BOX', 'POLICE_STATION')
              AND (latitude IS NULL OR longitude IS NULL)
            """, coordinate.latitude(), coordinate.longitude(), id);
    }

    private long remainingTargets() {
        return countByStatus("NOT_ATTEMPTED");
    }

    private long failedTargets() {
        return countByStatus("FAILED");
    }

    private long countByStatus(String status) {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM safe_infrastructure
            WHERE facility_type IN ('POLICE_BOX', 'POLICE_STATION')
              AND (latitude IS NULL OR longitude IS NULL)
              AND geocode_status = ?
            """, Long.class, status);
        return count == null ? 0 : count;
    }

    private long nullCoordinateTargets() {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM safe_infrastructure
            WHERE facility_type IN ('POLICE_BOX', 'POLICE_STATION')
              AND (latitude IS NULL OR longitude IS NULL)
            """, Long.class);
        return count == null ? 0 : count;
    }

    private void markFailed(long id, String reason) {
        jdbc.update("""
            UPDATE safe_infrastructure
            SET geocode_status = 'FAILED',
                geocode_attempts = geocode_attempts + 1,
                geocode_last_attempt_at = CURRENT_TIMESTAMP(6),
                geocode_error = ?,
                updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = ?
              AND facility_type IN ('POLICE_BOX', 'POLICE_STATION')
              AND (latitude IS NULL OR longitude IS NULL)
            """, truncate(reason, 255), id);
    }

    private static String errorSummary(Exception error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName()
            + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private static void sleep(long delayMillis) {
        if (delayMillis <= 0) return;
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private record Target(long id, String name, String address, String facilityType) {
    }

    public record BatchResult(
        int targets,
        int success,
        int failed,
        int skipped,
        int apiCalls,
        long remainingTargets,
        long failedTargets,
        long nullCoordinateTargets
    ) {
    }
}
