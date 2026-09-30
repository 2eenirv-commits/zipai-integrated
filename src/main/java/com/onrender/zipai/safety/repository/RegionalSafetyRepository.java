package com.onrender.zipai.safety.repository;

import com.onrender.zipai.safety.dto.RegionalSafetyIndex;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RegionalSafetyRepository {
    private final JdbcTemplate jdbc;

    public RegionalSafetyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Integer> latestYear() {
        Integer year = jdbc.queryForObject("SELECT MAX(`year`) FROM regional_safety_index", Integer.class);
        return Optional.ofNullable(year);
    }

    public Optional<RegionalSafetyIndex> findSido(int year, String sidoName) {
        return first(jdbc.query("""
            SELECT id, `year`, region_level, sido_name, sigungu_name,
                   traffic_grade, fire_grade, crime_grade, life_safety_grade,
                   suicide_grade, infectious_disease_grade, source_name, source_updated_at
            FROM regional_safety_index
            WHERE `year` = ? AND region_level = 'sido' AND sido_name = ?
            LIMIT 1
            """, this::mapRow, year, sidoName));
    }

    public Optional<RegionalSafetyIndex> findSigunguExact(int year, String sidoName, String sigunguName) {
        return first(jdbc.query("""
            SELECT id, `year`, region_level, sido_name, sigungu_name,
                   traffic_grade, fire_grade, crime_grade, life_safety_grade,
                   suicide_grade, infectious_disease_grade, source_name, source_updated_at
            FROM regional_safety_index
            WHERE `year` = ?
              AND region_level = 'sigungu'
              AND sido_name = ?
              AND sigungu_name = ?
            LIMIT 1
            """, this::mapRow, year, sidoName, sigunguName));
    }

    private RegionalSafetyIndex mapRow(java.sql.ResultSet rs, int rowNumber) throws java.sql.SQLException {
        String sigungu = rs.getString("sigungu_name");
        return new RegionalSafetyIndex(
            rs.getLong("id"), rs.getInt("year"), rs.getString("region_level"),
            rs.getString("sido_name"), sigungu == null || sigungu.isBlank() ? null : sigungu,
            rs.getInt("traffic_grade"), rs.getInt("fire_grade"), rs.getInt("crime_grade"),
            rs.getInt("life_safety_grade"), rs.getInt("suicide_grade"),
            rs.getInt("infectious_disease_grade"), rs.getString("source_name"),
            rs.getDate("source_updated_at").toLocalDate()
        );
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.stream().findFirst();
    }
}
