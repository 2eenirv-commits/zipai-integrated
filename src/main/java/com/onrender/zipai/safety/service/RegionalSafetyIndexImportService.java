package com.onrender.zipai.safety.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RegionalSafetyIndexImportService {
    private static final String[] REQUIRED_COLUMNS = {
        "year", "region_level", "sido_name", "sigungu_name",
        "traffic_grade", "fire_grade", "crime_grade", "life_safety_grade",
        "suicide_grade", "infectious_disease_grade", "source_name", "source_updated_at"
    };

    private final JdbcTemplate jdbc;

    public RegionalSafetyIndexImportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ImportResult importCsv(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<String> header = parseCsvLine(stripBom(reader.readLine()));
            Map<String, Integer> indexes = indexes(header);
            require(indexes, REQUIRED_COLUMNS);
            int read = 0;
            int succeeded = 0;
            int errors = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                read++;
                try {
                    upsert(row(parseCsvLine(line), indexes));
                    succeeded++;
                } catch (RuntimeException error) {
                    errors++;
                }
            }
            return new ImportResult(read, succeeded, errors, countRows());
        }
    }

    private void upsert(Row row) {
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO regional_safety_index
                  (`year`, region_level, sido_name, sigungu_name,
                   traffic_grade, fire_grade, crime_grade, life_safety_grade,
                   suicide_grade, infectious_disease_grade, source_name, source_updated_at,
                   created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6))
                ON DUPLICATE KEY UPDATE
                  traffic_grade = VALUES(traffic_grade), fire_grade = VALUES(fire_grade),
                  crime_grade = VALUES(crime_grade), life_safety_grade = VALUES(life_safety_grade),
                  suicide_grade = VALUES(suicide_grade),
                  infectious_disease_grade = VALUES(infectious_disease_grade),
                  source_name = VALUES(source_name), source_updated_at = VALUES(source_updated_at),
                  updated_at = NOW(6)
                """);
            statement.setInt(1, row.year());
            statement.setString(2, row.regionLevel());
            statement.setString(3, row.sidoName());
            statement.setString(4, row.sigunguName());
            statement.setInt(5, row.trafficGrade());
            statement.setInt(6, row.fireGrade());
            statement.setInt(7, row.crimeGrade());
            statement.setInt(8, row.lifeSafetyGrade());
            statement.setInt(9, row.suicideGrade());
            statement.setInt(10, row.infectiousDiseaseGrade());
            statement.setString(11, row.sourceName());
            statement.setDate(12, Date.valueOf(row.sourceUpdatedAt()));
            return statement;
        });
    }

    private Row row(List<String> values, Map<String, Integer> indexes) {
        int year = integer(values, indexes, "year");
        String level = value(values, indexes, "region_level");
        String sido = value(values, indexes, "sido_name");
        String sigungu = value(values, indexes, "sigungu_name");
        if (!(level.equals("sido") || level.equals("sigungu"))) {
            throw new IllegalArgumentException("region_level must be sido or sigungu");
        }
        if (sido.isBlank() || (level.equals("sigungu") && sigungu.isBlank())) {
            throw new IllegalArgumentException("Required region name is blank");
        }
        if (level.equals("sido") && !sigungu.isBlank()) {
            throw new IllegalArgumentException("sido row must not contain sigungu_name");
        }
        int traffic = grade(values, indexes, "traffic_grade");
        int fire = grade(values, indexes, "fire_grade");
        int crime = grade(values, indexes, "crime_grade");
        int life = grade(values, indexes, "life_safety_grade");
        int suicide = grade(values, indexes, "suicide_grade");
        int infectious = grade(values, indexes, "infectious_disease_grade");
        String source = value(values, indexes, "source_name");
        if (source.isBlank()) throw new IllegalArgumentException("source_name is blank");
        LocalDate sourceDate = LocalDate.parse(value(values, indexes, "source_updated_at"));
        return new Row(year, level, sido, sigungu, traffic, fire, crime, life, suicide, infectious, source, sourceDate);
    }

    private int grade(List<String> values, Map<String, Integer> indexes, String column) {
        int grade = integer(values, indexes, column);
        if (grade < 1 || grade > 5) throw new IllegalArgumentException(column + " must be between 1 and 5");
        return grade;
    }

    private long countRows() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM regional_safety_index", Long.class);
        return count == null ? 0 : count;
    }

    private static int integer(List<String> values, Map<String, Integer> indexes, String column) {
        return Integer.parseInt(value(values, indexes, column));
    }

    private static String value(List<String> values, Map<String, Integer> indexes, String column) {
        int index = indexes.get(column);
        return index < values.size() ? values.get(index).trim() : "";
    }

    private static Map<String, Integer> indexes(List<String> header) {
        Map<String, Integer> result = new HashMap<>();
        for (int index = 0; index < header.size(); index++) result.put(header.get(index), index);
        return result;
    }

    private static void require(Map<String, Integer> indexes, String... columns) {
        for (String column : columns) {
            if (!indexes.containsKey(column)) throw new IllegalArgumentException("CSV column missing: " + column);
        }
    }

    private static String stripBom(String line) {
        if (line == null) throw new IllegalArgumentException("CSV is empty");
        return line.startsWith("\uFEFF") ? line.substring(1) : line;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(character);
            }
        }
        values.add(value.toString());
        return values;
    }

    private record Row(
        int year, String regionLevel, String sidoName, String sigunguName,
        int trafficGrade, int fireGrade, int crimeGrade, int lifeSafetyGrade,
        int suicideGrade, int infectiousDiseaseGrade, String sourceName, LocalDate sourceUpdatedAt
    ) {
    }

    public record ImportResult(int readRows, int succeededRows, int errorRows, long totalRows) {
    }
}
