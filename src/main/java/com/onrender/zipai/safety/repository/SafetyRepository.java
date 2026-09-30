package com.onrender.zipai.safety.repository;

import com.onrender.zipai.safety.dto.SafetyFacility;
import com.onrender.zipai.safety.dto.SafetyLocation;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SafetyRepository {
    private final JdbcTemplate jdbc;

    public SafetyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SafetyLocation> searchLocations(String keyword) {
        return jdbc.query("""
            SELECT id, name, address, latitude, longitude, source_name, source_updated_at
            FROM safe_area_centers
            WHERE name LIKE ? OR address LIKE ?
            ORDER BY id
            LIMIT 5
            """, (rs, rowNum) -> new SafetyLocation(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("address"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getString("source_name"),
                rs.getDate("source_updated_at").toLocalDate()
            ), "%" + keyword + "%", "%" + keyword + "%");
    }

    public List<SafetyFacility> findFacilities(double latitude, double longitude, int radiusMeters) {
        double latDelta = radiusMeters / 111_320.0;
        double lngDelta = radiusMeters / (111_320.0 * Math.max(0.2, Math.cos(Math.toRadians(latitude))));
        return jdbc.query("""
    SELECT id, source_id, facility_type, name, address, latitude, longitude,
           purpose, camera_count, source_name, source_updated_at
    FROM safe_infrastructure
    WHERE latitude BETWEEN ? AND ?
      AND longitude BETWEEN ? AND ?
      AND latitude IS NOT NULL
      AND longitude IS NOT NULL

      AND NOT (
          facility_type = 'CCTV'
          AND latitude = 36
          AND longitude = 127
      )

      AND NOT (
          facility_type IN ('STREET_LIGHT', 'SECURITY_LIGHT')
          AND latitude = 36.561834
          AND longitude = 128.699220
      )

      AND NOT (
          facility_type IN ('SAFETY_BELL', 'EMERGENCY_BELL')
          AND latitude = 36.9582
          AND longitude = 127.4316
      )

      AND NOT (
        facility_type IN ('SAFETY_BELL', 'EMERGENCY_BELL')
        AND latitude = 36
        AND longitude = 127
)
    """, (rs, rowNum) -> {
            double facilityLat = rs.getDouble("latitude");
            double facilityLng = rs.getDouble("longitude");
            long distance = Math.round(calculateDistance(latitude, longitude, facilityLat, facilityLng));
            String facilityType = normalizeFacilityType(rs.getString("facility_type"));
            return new SafetyFacility(
                rs.getLong("id"),
                rs.getString("source_id"),
                facilityType,
                label(facilityType),
                rs.getString("name"),
                rs.getString("address"),
                facilityLat,
                facilityLng,
                rs.getString("purpose"),
                rs.getInt("camera_count"),
                distance,
                rs.getString("source_name"),
                rs.getDate("source_updated_at").toLocalDate()
            );
        }, latitude - latDelta, latitude + latDelta, longitude - lngDelta, longitude + lngDelta).stream()
            .filter(item -> item.distanceMeters() <= radiusMeters)
            .sorted((left, right) -> Long.compare(left.distanceMeters(), right.distanceMeters()))
            .toList();
    }

    public LocalDate latestSourceDate() {
        LocalDate infraDate = jdbc.queryForObject(
            "SELECT MAX(source_updated_at) FROM safe_infrastructure", LocalDate.class);
        return infraDate == null ? LocalDate.now() : infraDate;
    }

    public long countFacilities() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM safe_infrastructure", Long.class);
        return count == null ? 0 : count;
    }

    public boolean existsFacilityType(String facilityType) {
        String normalized = normalizeFacilityType(facilityType);
        List<String> databaseTypes = switch (normalized) {
            case "SAFETY_BELL" -> List.of("SAFETY_BELL", "EMERGENCY_BELL");
            case "STREET_LIGHT" -> List.of("STREET_LIGHT", "SECURITY_LIGHT");
            default -> List.of(normalized);
        };

        String placeholders = String.join(",", databaseTypes.stream().map(value -> "?").toList());
        Integer exists = jdbc.query(
            "SELECT 1 FROM safe_infrastructure WHERE facility_type IN (" + placeholders + ") LIMIT 1",
            rs -> rs.next() ? 1 : 0,
            databaseTypes.toArray()
        );
        return exists != null && exists == 1;
    }

    private static String normalizeFacilityType(String type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case "EMERGENCY_BELL" -> "SAFETY_BELL";
            case "SECURITY_LIGHT" -> "STREET_LIGHT";
            default -> type;
        };
    }

    private static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String label(String type) {
        return switch (type) {
            case "CCTV" -> "CCTV";
            case "POLICE_STATION" -> "경찰서";
            case "POLICE_BOX" -> "지구대·파출소";
            case "SAFETY_BELL" -> "안전비상벨";
            case "STREET_LIGHT" -> "가로등";
            default -> "안전시설";
        };
    }
}
