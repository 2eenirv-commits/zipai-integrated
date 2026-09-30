package com.onrender.zipai.safety.repository;

import com.onrender.zipai.safety.dto.CrimeTypeStatistic;
import com.onrender.zipai.safety.dto.WomenSafetyGuardHouse;
import com.onrender.zipai.safety.dto.WomenSafetyFacility;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SeoulSafetyInformationRepository {
    private final JdbcTemplate jdbc;

    public SeoulSafetyInformationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CrimeRow> findCrimeStatistics(
        int year, String sidoName, String sigunguName, String policeAgency, String coverageType
    ) {
        String districtFilter = sigunguName == null || sigunguName.isBlank() ? "" : " AND sigungu_name = ?";
        String sql = """
            SELECT police_station_name, sigungu_name, police_agency, provisional, coverage_type,
                   coverage_note, crime_type, occurrence_count, arrest_count, source_name, source_updated_at
            FROM police_crime_statistics
            WHERE `year` = ? AND sido_name = ? AND police_agency = ? AND coverage_type = ?
            """ + districtFilter + """
            ORDER BY police_station_name,
              CASE crime_type WHEN '살인' THEN 1 WHEN '강도' THEN 2 WHEN '강간·추행' THEN 3
                              WHEN '절도' THEN 4 WHEN '폭력' THEN 5 ELSE 6 END
            """;
        Object[] arguments = districtFilter.isEmpty()
            ? new Object[]{year, sidoName, policeAgency, coverageType}
            : new Object[]{year, sidoName, policeAgency, coverageType, sigunguName};
        return jdbc.query(sql, (rs, rowNumber) -> new CrimeRow(
            rs.getString("police_station_name"), rs.getString("sigungu_name"),
            new CrimeTypeStatistic(rs.getString("crime_type"), rs.getInt("occurrence_count"), rs.getInt("arrest_count")),
            rs.getString("police_agency"), rs.getBoolean("provisional"), rs.getString("coverage_type"),
            rs.getString("coverage_note"), rs.getString("source_name"), rs.getDate("source_updated_at").toLocalDate()
        ), arguments);
    }

    public List<WomenSafetyGuardHouse> findGuardHouses(double latitude, double longitude, int radiusMeters) {
        double latDelta = radiusMeters / 111_320.0;
        double lngDelta = radiusMeters / (111_320.0 * Math.max(0.2, Math.cos(Math.toRadians(latitude))));
        return jdbc.query("""
            SELECT id, source_id, brand_name, store_name, sido_name, sigungu_name, address,
                   latitude, longitude, source_name, source_updated_at
            FROM women_safety_guard_house
            WHERE latitude IS NOT NULL AND longitude IS NOT NULL
              AND geocode_status = 'VERIFIED'
              AND geocoded_sido = sido_name AND geocoded_sigungu = sigungu_name
              AND latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?
            """, (rs, rowNumber) -> {
                double itemLatitude = rs.getDouble("latitude");
                double itemLongitude = rs.getDouble("longitude");
                return new WomenSafetyGuardHouse(
                    rs.getLong("id"), rs.getString("source_id"), rs.getString("brand_name"),
                    rs.getString("store_name"), rs.getString("sido_name"), rs.getString("sigungu_name"),
                    rs.getString("address"), itemLatitude, itemLongitude,
                    Math.round(distance(latitude, longitude, itemLatitude, itemLongitude)),
                    rs.getString("source_name"), rs.getDate("source_updated_at").toLocalDate()
                );
            }, latitude - latDelta, latitude + latDelta, longitude - lngDelta, longitude + lngDelta).stream()
            .filter(item -> item.distanceMeters() <= radiusMeters)
            .sorted((left, right) -> Long.compare(left.distanceMeters(), right.distanceMeters()))
            .toList();
    }

    public Optional<LocalDate> latestGuardHouseDate() {
        return Optional.ofNullable(jdbc.queryForObject(
            """
            SELECT MAX(source_updated_at) FROM women_safety_guard_house
            WHERE latitude IS NOT NULL AND longitude IS NOT NULL
              AND geocode_status = 'VERIFIED'
              AND geocoded_sido = sido_name AND geocoded_sigungu = sigungu_name
            """,
            LocalDate.class
        ));
    }

    public List<WomenSafetyFacility> findWomenSafetyFacilities(
        double latitude, double longitude, int radiusMeters, String sidoName
    ) {
        double latDelta = radiusMeters / 111_320.0;
        double lngDelta = radiusMeters / (111_320.0 * Math.max(0.2, Math.cos(Math.toRadians(latitude))));
        String sidoFilter = sidoName == null || sidoName.isBlank() ? "" : " AND sido_name = ?";
        String sql = """
            SELECT id, source_id, sido_name, sigungu_name, facility_type, facility_name, brand_name,
                   address, latitude, longitude, source_name, source_updated_at, data_year, coverage_note
            FROM women_safety_facility
            WHERE coordinate_validation = 'VERIFIED' AND latitude IS NOT NULL AND longitude IS NOT NULL
              AND latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?
            """ + sidoFilter;
        Object[] arguments = sidoFilter.isEmpty()
            ? new Object[]{latitude - latDelta, latitude + latDelta, longitude - lngDelta, longitude + lngDelta}
            : new Object[]{latitude - latDelta, latitude + latDelta, longitude - lngDelta, longitude + lngDelta, sidoName};
        return jdbc.query(sql, (rs, rowNumber) -> {
            double itemLatitude = rs.getDouble("latitude");
            double itemLongitude = rs.getDouble("longitude");
            Integer dataYear = rs.getObject("data_year", Integer.class);
            return new WomenSafetyFacility(
                rs.getLong("id"), rs.getString("source_id"), rs.getString("sido_name"),
                rs.getString("sigungu_name"), rs.getString("facility_type"), rs.getString("facility_name"),
                rs.getString("brand_name"), rs.getString("address"), itemLatitude, itemLongitude,
                Math.round(distance(latitude, longitude, itemLatitude, itemLongitude)),
                rs.getString("source_name"), rs.getDate("source_updated_at").toLocalDate(),
                dataYear, rs.getString("coverage_note")
            );
        }, arguments).stream()
            .filter(item -> item.distanceMeters() <= radiusMeters)
            .sorted((left, right) -> Long.compare(left.distanceMeters(), right.distanceMeters()))
            .toList();
    }

    public List<String> supportedWomenSafetyRegions(String sidoName) {
        return jdbc.queryForList("""
            SELECT DISTINCT sigungu_name FROM women_safety_facility
            WHERE sido_name = ? AND coordinate_validation = 'VERIFIED'
              AND latitude IS NOT NULL AND longitude IS NOT NULL
            ORDER BY sigungu_name
            """, String.class, sidoName);
    }

    private static double distance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public record CrimeRow(
        String policeStationName,
        String sigunguName,
        CrimeTypeStatistic statistic,
        String policeAgency,
        boolean provisional,
        String coverageType,
        String coverageNote,
        String sourceName,
        LocalDate sourceUpdatedAt
    ) {
    }
}
