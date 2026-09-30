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
public class SeoulSafetyDataImportService {
    private final JdbcTemplate jdbc;

    public SeoulSafetyDataImportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ImportResult importCrime(Path path) throws IOException {
        return importRows(path, new String[]{
            "year", "sido_name", "sigungu_name", "police_station_name", "crime_type",
            "occurrence_count", "arrest_count", "source_name", "source_updated_at"
        }, this::upsertCrime, "seoul_police_crime_statistics");
    }

    public ImportResult importGuardHouses(Path path) throws IOException {
        return importRows(path, new String[]{
            "source_id", "brand_name", "store_name", "sido_name", "sigungu_name", "address",
            "latitude", "longitude", "geocode_status", "geocoded_address", "geocoded_sido",
            "geocoded_sigungu", "source_name", "source_updated_at"
        }, this::upsertGuardHouse, "women_safety_guard_house");
    }

    private ImportResult importRows(Path path, String[] required, RowConsumer consumer, String table) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<String> header = parse(stripBom(reader.readLine()));
            Map<String, Integer> indexes = indexes(header);
            for (String column : required) {
                if (!indexes.containsKey(column)) throw new IllegalArgumentException("CSV column missing: " + column);
            }
            int read = 0;
            int succeeded = 0;
            int errors = 0;
            String line;
            StringBuilder record = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (record.isEmpty() && line.isBlank()) continue;
                if (!record.isEmpty()) record.append('\n');
                record.append(line);
                if (hasOpenQuote(record)) continue;
                read++;
                try {
                    consumer.accept(parse(record.toString()), indexes);
                    succeeded++;
                } catch (RuntimeException error) {
                    errors++;
                }
                record.setLength(0);
            }
            if (!record.isEmpty()) throw new IllegalArgumentException("Unclosed quoted CSV record");
            Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            return new ImportResult(read, succeeded, errors, total == null ? 0 : total);
        }
    }

    private void upsertCrime(List<String> values, Map<String, Integer> indexes) {
        int year = integer(values, indexes, "year");
        String sido = required(values, indexes, "sido_name");
        String sigungu = required(values, indexes, "sigungu_name");
        String station = required(values, indexes, "police_station_name");
        String type = required(values, indexes, "crime_type");
        int occurrence = nonNegative(values, indexes, "occurrence_count");
        int arrest = nonNegative(values, indexes, "arrest_count");
        String source = required(values, indexes, "source_name");
        LocalDate sourceDate = LocalDate.parse(required(values, indexes, "source_updated_at"));
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO seoul_police_crime_statistics
                  (`year`, sido_name, sigungu_name, police_station_name, crime_type,
                   occurrence_count, arrest_count, source_name, source_updated_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6))
                ON DUPLICATE KEY UPDATE
                  sido_name=VALUES(sido_name), sigungu_name=VALUES(sigungu_name),
                  occurrence_count=VALUES(occurrence_count), arrest_count=VALUES(arrest_count),
                  source_name=VALUES(source_name), source_updated_at=VALUES(source_updated_at), updated_at=NOW(6)
                """);
            statement.setInt(1, year);
            statement.setString(2, sido);
            statement.setString(3, sigungu);
            statement.setString(4, station);
            statement.setString(5, type);
            statement.setInt(6, occurrence);
            statement.setInt(7, arrest);
            statement.setString(8, source);
            statement.setDate(9, Date.valueOf(sourceDate));
            return statement;
        });
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO police_crime_statistics
                  (`year`, sido_name, sigungu_name, police_station_name, police_agency, crime_type,
                   occurrence_count, arrest_count, provisional, coverage_type, coverage_note,
                   source_name, source_updated_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, '서울특별시경찰청', ?, ?, ?, FALSE, 'POLICE_STATION_STATISTICS',
                        '경찰서 관할 기준 통계입니다.', ?, ?, NOW(6), NOW(6))
                ON DUPLICATE KEY UPDATE
                  sido_name=VALUES(sido_name), sigungu_name=VALUES(sigungu_name),
                  occurrence_count=VALUES(occurrence_count), arrest_count=VALUES(arrest_count),
                  source_name=VALUES(source_name), source_updated_at=VALUES(source_updated_at), updated_at=NOW(6)
                """);
            statement.setInt(1, year);
            statement.setString(2, sido);
            statement.setString(3, sigungu);
            statement.setString(4, station);
            statement.setString(5, type);
            statement.setInt(6, occurrence);
            statement.setInt(7, arrest);
            statement.setString(8, source);
            statement.setDate(9, Date.valueOf(sourceDate));
            return statement;
        });
    }

    private void upsertGuardHouse(List<String> values, Map<String, Integer> indexes) {
        String sourceId = required(values, indexes, "source_id");
        String brand = required(values, indexes, "brand_name");
        String store = value(values, indexes, "store_name");
        String sido = required(values, indexes, "sido_name");
        String sigungu = required(values, indexes, "sigungu_name");
        String address = required(values, indexes, "address");
        Double latitude = decimal(values, indexes, "latitude");
        Double longitude = decimal(values, indexes, "longitude");
        String geocodeStatus = required(values, indexes, "geocode_status");
        String geocodedAddress = value(values, indexes, "geocoded_address");
        String geocodedSido = value(values, indexes, "geocoded_sido");
        String geocodedSigungu = value(values, indexes, "geocoded_sigungu");
        if ((latitude == null) != (longitude == null)) throw new IllegalArgumentException("latitude/longitude must be paired");
        if (latitude != null && (!Double.isFinite(latitude) || latitude < -90 || latitude > 90)) {
            throw new IllegalArgumentException("invalid latitude");
        }
        if (longitude != null && (!Double.isFinite(longitude) || longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("invalid longitude");
        }
        boolean verified = "VERIFIED".equals(geocodeStatus)
            && sido.equals(geocodedSido) && sigungu.equals(geocodedSigungu);
        if (!verified) {
            latitude = null;
            longitude = null;
        }
        Double safeLatitude = latitude;
        Double safeLongitude = longitude;
        String source = required(values, indexes, "source_name");
        LocalDate sourceDate = LocalDate.parse(required(values, indexes, "source_updated_at"));
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO women_safety_guard_house
                  (source_id, brand_name, store_name, sido_name, sigungu_name, address,
                   latitude, longitude, geocode_status, geocoded_address, geocoded_sido, geocoded_sigungu,
                   source_name, source_updated_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6))
                ON DUPLICATE KEY UPDATE
                  brand_name=VALUES(brand_name), store_name=VALUES(store_name),
                  sido_name=VALUES(sido_name), sigungu_name=VALUES(sigungu_name), address=VALUES(address),
                  latitude=VALUES(latitude), longitude=VALUES(longitude),
                  geocode_status=VALUES(geocode_status), geocoded_address=VALUES(geocoded_address),
                  geocoded_sido=VALUES(geocoded_sido), geocoded_sigungu=VALUES(geocoded_sigungu),
                  source_updated_at=VALUES(source_updated_at), updated_at=NOW(6)
                """);
            statement.setString(1, sourceId);
            statement.setString(2, brand);
            statement.setString(3, store);
            statement.setString(4, sido);
            statement.setString(5, sigungu);
            statement.setString(6, address);
            if (safeLatitude == null) statement.setNull(7, java.sql.Types.DECIMAL); else statement.setDouble(7, safeLatitude);
            if (safeLongitude == null) statement.setNull(8, java.sql.Types.DECIMAL); else statement.setDouble(8, safeLongitude);
            statement.setString(9, geocodeStatus);
            statement.setString(10, geocodedAddress);
            statement.setString(11, geocodedSido);
            statement.setString(12, geocodedSigungu);
            statement.setString(13, source);
            statement.setDate(14, Date.valueOf(sourceDate));
            return statement;
        });
        String facilityName = (brand + " " + store).trim();
        String coordinateValidation = verified ? "VERIFIED" : geocodeStatus;
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO women_safety_facility
                  (source_id, sido_name, sigungu_name, facility_type, facility_name, brand_name, address,
                   latitude, longitude, source_name, source_updated_at, data_year, coverage_note,
                   coordinate_validation, created_at, updated_at)
                VALUES (?, ?, ?, 'SAFE_HOUSE', ?, ?, ?, ?, ?, ?, ?, 2019,
                        '서울특별시 공식 여성안심지킴이집입니다.', ?, NOW(6), NOW(6))
                ON DUPLICATE KEY UPDATE
                  sido_name=VALUES(sido_name), sigungu_name=VALUES(sigungu_name),
                  facility_type=VALUES(facility_type), facility_name=VALUES(facility_name),
                  brand_name=VALUES(brand_name), address=VALUES(address),
                  latitude=VALUES(latitude), longitude=VALUES(longitude),
                  source_updated_at=VALUES(source_updated_at), data_year=VALUES(data_year),
                  coverage_note=VALUES(coverage_note),
                  coordinate_validation=VALUES(coordinate_validation), updated_at=NOW(6)
                """);
            statement.setString(1, sourceId);
            statement.setString(2, sido);
            statement.setString(3, sigungu);
            statement.setString(4, facilityName);
            statement.setString(5, brand);
            statement.setString(6, address);
            if (safeLatitude == null) statement.setNull(7, java.sql.Types.DECIMAL); else statement.setDouble(7, safeLatitude);
            if (safeLongitude == null) statement.setNull(8, java.sql.Types.DECIMAL); else statement.setDouble(8, safeLongitude);
            statement.setString(9, source);
            statement.setDate(10, Date.valueOf(sourceDate));
            statement.setString(11, coordinateValidation);
            return statement;
        });
    }

    private static int nonNegative(List<String> values, Map<String, Integer> indexes, String column) {
        int number = integer(values, indexes, column);
        if (number < 0) throw new IllegalArgumentException(column + " must not be negative");
        return number;
    }

    private static int integer(List<String> values, Map<String, Integer> indexes, String column) {
        return Integer.parseInt(required(values, indexes, column));
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

    private static boolean hasOpenQuote(CharSequence text) {
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) != '"') continue;
            if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                index++;
            } else {
                quoted = !quoted;
            }
        }
        return quoted;
    }

    @FunctionalInterface
    private interface RowConsumer {
        void accept(List<String> values, Map<String, Integer> indexes);
    }

    public record ImportResult(int readRows, int succeededRows, int errorRows, long totalRows) {
    }
}
