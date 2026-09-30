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
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PoliceCrimeStatisticsImportService {
    private static final Set<String> CRIME_TYPES = Set.of("살인", "강도", "강간·추행", "절도", "폭력");
    private static final Set<String> COVERAGE_TYPES = Set.of("POLICE_STATION_STATISTICS", "POLICE_AGENCY_STATISTICS");
    private static final String[] REQUIRED = {
        "year", "sido_name", "sigungu_name", "police_station_name", "police_agency",
        "crime_type", "occurrence_count", "arrest_count", "provisional", "coverage_type",
        "coverage_note", "source_name", "source_updated_at"
    };

    private final JdbcTemplate jdbc;

    public PoliceCrimeStatisticsImportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ImportResult importCsv(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<String, Integer> indexes = indexes(parse(stripBom(reader.readLine())));
            for (String column : REQUIRED) {
                if (!indexes.containsKey(column)) throw new IllegalArgumentException("CSV column missing: " + column);
            }
            int read = 0;
            int succeeded = 0;
            int errors = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                read++;
                try {
                    upsert(parse(line), indexes);
                    succeeded++;
                } catch (RuntimeException error) {
                    errors++;
                }
            }
            Long total = jdbc.queryForObject("SELECT COUNT(*) FROM police_crime_statistics", Long.class);
            return new ImportResult(read, succeeded, errors, total == null ? 0 : total);
        }
    }

    private void upsert(List<String> values, Map<String, Integer> indexes) {
        int year = integer(values, indexes, "year");
        String sido = required(values, indexes, "sido_name");
        String sigungu = value(values, indexes, "sigungu_name");
        String station = required(values, indexes, "police_station_name");
        String agency = required(values, indexes, "police_agency");
        String crimeType = required(values, indexes, "crime_type");
        int occurrence = nonNegative(values, indexes, "occurrence_count");
        int arrest = nonNegative(values, indexes, "arrest_count");
        String provisionalValue = required(values, indexes, "provisional");
        if (!Set.of("true", "false").contains(provisionalValue.toLowerCase())) {
            throw new IllegalArgumentException("provisional must be true or false");
        }
        boolean provisional = Boolean.parseBoolean(provisionalValue);
        String coverageType = required(values, indexes, "coverage_type");
        String coverageNote = required(values, indexes, "coverage_note");
        String source = required(values, indexes, "source_name");
        LocalDate sourceDate = LocalDate.parse(required(values, indexes, "source_updated_at"));
        if (year < 1900 || year > LocalDate.now().getYear()) throw new IllegalArgumentException("invalid year");
        if (!CRIME_TYPES.contains(crimeType)) throw new IllegalArgumentException("invalid crime_type");
        if (!COVERAGE_TYPES.contains(coverageType)) throw new IllegalArgumentException("invalid coverage_type");
        if ("POLICE_STATION_STATISTICS".equals(coverageType) && sigungu.isBlank()) {
            throw new IllegalArgumentException("station statistics require sigungu_name");
        }
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO police_crime_statistics
                  (`year`, sido_name, sigungu_name, police_station_name, police_agency, crime_type,
                   occurrence_count, arrest_count, provisional, coverage_type, coverage_note,
                   source_name, source_updated_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6))
                ON DUPLICATE KEY UPDATE
                  sido_name=VALUES(sido_name), sigungu_name=VALUES(sigungu_name),
                  occurrence_count=VALUES(occurrence_count), arrest_count=VALUES(arrest_count),
                  provisional=VALUES(provisional), coverage_type=VALUES(coverage_type),
                  coverage_note=VALUES(coverage_note), source_name=VALUES(source_name),
                  source_updated_at=VALUES(source_updated_at), updated_at=NOW(6)
                """);
            statement.setInt(1, year);
            statement.setString(2, sido);
            statement.setString(3, sigungu);
            statement.setString(4, station);
            statement.setString(5, agency);
            statement.setString(6, crimeType);
            statement.setInt(7, occurrence);
            statement.setInt(8, arrest);
            statement.setBoolean(9, provisional);
            statement.setString(10, coverageType);
            statement.setString(11, coverageNote);
            statement.setString(12, source);
            statement.setDate(13, Date.valueOf(sourceDate));
            return statement;
        });
    }

    private static int nonNegative(List<String> values, Map<String, Integer> indexes, String column) {
        int result = integer(values, indexes, column);
        if (result < 0) throw new IllegalArgumentException(column + " must not be negative");
        return result;
    }

    private static int integer(List<String> values, Map<String, Integer> indexes, String column) {
        return Integer.parseInt(required(values, indexes, column));
    }

    private static String required(List<String> values, Map<String, Integer> indexes, String column) {
        String result = value(values, indexes, column);
        if (result.isBlank()) throw new IllegalArgumentException(column + " is blank");
        return result;
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

    private static String stripBom(String line) {
        if (line == null) throw new IllegalArgumentException("CSV is empty");
        return line.startsWith("\uFEFF") ? line.substring(1) : line;
    }

    private static List<String> parse(String line) {
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
            }
            else if (character == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else value.append(character);
        }
        values.add(value.toString());
        return values;
    }

    public record ImportResult(int readRows, int succeededRows, int errorRows, long totalRows) {
    }
}
