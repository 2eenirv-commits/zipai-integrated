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
public class WomenSafetyFacilityImportService {
    private static final Set<String> TYPES = Set.of("SAFE_HOUSE", "SAFE_STORE", "SAFE_PARCEL_LOCKER", "SAFE_ROUTE", "OTHER");
    private static final String[] REQUIRED = {
        "source_id", "sido_name", "sigungu_name", "facility_type", "facility_name", "brand_name",
        "address", "latitude", "longitude", "source_name", "source_updated_at", "data_year",
        "coverage_note", "coordinate_validation"
    };
    private final JdbcTemplate jdbc;

    public WomenSafetyFacilityImportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ImportResult importCsv(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<String, Integer> indexes = indexes(parse(stripBom(reader.readLine())));
            for (String column : REQUIRED) if (!indexes.containsKey(column)) throw new IllegalArgumentException("CSV column missing: " + column);
            int read = 0, succeeded = 0, errors = 0;
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
            Long total = jdbc.queryForObject("SELECT COUNT(*) FROM women_safety_facility", Long.class);
            Long searchable = jdbc.queryForObject("SELECT COUNT(*) FROM women_safety_facility WHERE coordinate_validation='VERIFIED' AND latitude IS NOT NULL AND longitude IS NOT NULL", Long.class);
            return new ImportResult(read, succeeded, errors, total == null ? 0 : total, searchable == null ? 0 : searchable);
        }
    }

    private void upsert(List<String> values, Map<String, Integer> indexes) {
        String sourceId = required(values, indexes, "source_id");
        String sido = required(values, indexes, "sido_name");
        String sigungu = required(values, indexes, "sigungu_name");
        String type = required(values, indexes, "facility_type");
        if (!TYPES.contains(type)) throw new IllegalArgumentException("invalid facility_type");
        String name = required(values, indexes, "facility_name");
        String brand = value(values, indexes, "brand_name");
        String address = required(values, indexes, "address");
        Double latitude = decimal(values, indexes, "latitude");
        Double longitude = decimal(values, indexes, "longitude");
        String source = required(values, indexes, "source_name");
        LocalDate sourceDate = LocalDate.parse(required(values, indexes, "source_updated_at"));
        String yearText = value(values, indexes, "data_year");
        Integer dataYear = yearText.isBlank() ? null : Integer.valueOf(yearText);
        String note = required(values, indexes, "coverage_note");
        String validation = required(values, indexes, "coordinate_validation");
        if ((latitude == null) != (longitude == null)) throw new IllegalArgumentException("latitude/longitude must be paired");
        if (!"VERIFIED".equals(validation)) {
            latitude = null;
            longitude = null;
        }
        if ("VERIFIED".equals(validation) && (latitude == null || latitude < 36.8 || latitude > 38.3 || longitude < 126.3 || longitude > 127.9)) {
            throw new IllegalArgumentException("verified coordinate is outside Gyeonggi bounds");
        }
        Double safeLatitude = latitude, safeLongitude = longitude;
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO women_safety_facility
                  (source_id, sido_name, sigungu_name, facility_type, facility_name, brand_name, address,
                   latitude, longitude, source_name, source_updated_at, data_year, coverage_note,
                   coordinate_validation, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6))
                ON DUPLICATE KEY UPDATE
                  sido_name=VALUES(sido_name), sigungu_name=VALUES(sigungu_name), facility_type=VALUES(facility_type),
                  facility_name=VALUES(facility_name), brand_name=VALUES(brand_name), address=VALUES(address),
                  latitude=VALUES(latitude), longitude=VALUES(longitude), source_updated_at=VALUES(source_updated_at),
                  data_year=VALUES(data_year), coverage_note=VALUES(coverage_note),
                  coordinate_validation=VALUES(coordinate_validation), updated_at=NOW(6)
                """);
            statement.setString(1, sourceId); statement.setString(2, sido); statement.setString(3, sigungu);
            statement.setString(4, type); statement.setString(5, name); statement.setString(6, brand);
            statement.setString(7, address);
            if (safeLatitude == null) statement.setNull(8, java.sql.Types.DECIMAL); else statement.setDouble(8, safeLatitude);
            if (safeLongitude == null) statement.setNull(9, java.sql.Types.DECIMAL); else statement.setDouble(9, safeLongitude);
            statement.setString(10, source); statement.setDate(11, Date.valueOf(sourceDate));
            if (dataYear == null) statement.setNull(12, java.sql.Types.SMALLINT); else statement.setInt(12, dataYear);
            statement.setString(13, note); statement.setString(14, validation);
            return statement;
        });
    }

    private static Double decimal(List<String> values, Map<String, Integer> indexes, String column) {
        String text = value(values, indexes, column);
        return text.isBlank() ? null : Double.valueOf(text);
    }

    private static String required(List<String> values, Map<String, Integer> indexes, String column) {
        String text = value(values, indexes, column);
        if (text.isBlank()) throw new IllegalArgumentException(column + " is blank");
        return text;
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
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') { value.append('"'); index++; }
                else quoted = !quoted;
            } else if (character == ',' && !quoted) { values.add(value.toString()); value.setLength(0); }
            else value.append(character);
        }
        values.add(value.toString());
        return values;
    }

    public record ImportResult(int readRows, int succeededRows, int errorRows, long totalRows, long searchableRows) {
    }
}
