package com.onrender.zipai.safety.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SafetyFacilityImportService {
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final int PROGRESS_INTERVAL = 10_000;

    private static final String UPSERT_SQL = """
        INSERT INTO safe_infrastructure
          (source_id, facility_type, name, address, latitude, longitude, purpose,
           camera_count, source_name, source_updated_at, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6))
        ON DUPLICATE KEY UPDATE
          facility_type = VALUES(facility_type),
          name = VALUES(name),
          address = VALUES(address),
          latitude = COALESCE(VALUES(latitude), latitude),
          longitude = COALESCE(VALUES(longitude), longitude),
          purpose = VALUES(purpose),
          camera_count = VALUES(camera_count),
          source_name = VALUES(source_name),
          source_updated_at = VALUES(source_updated_at),
          updated_at = NOW(6)
        """;

    private final JdbcTemplate jdbc;

    public SafetyFacilityImportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ImportResult importCsv(Path path) throws IOException {
        ensureSchema();

        int batchSize = positiveIntEnv("SAFETY_IMPORT_BATCH_SIZE", DEFAULT_BATCH_SIZE);
        Path rejectedPool = rejectedPoolPath(path);
        Files.createDirectories(rejectedPool.toAbsolutePath().getParent());
        Instant startedAt = Instant.now();

        int read = 0;
        int succeeded = 0;
        int errors = 0;

        try (
            BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
            BufferedWriter rejectedWriter = Files.newBufferedWriter(rejectedPool, StandardCharsets.UTF_8)
        ) {
            String headerRecord = readCsvRecord(reader);
            List<String> header = parseCsvRecord(stripBom(headerRecord));
            Map<String, Integer> indexes = indexes(header);

            require(
                indexes,
                "source_id", "name", "facility_type", "address",
                "latitude", "longitude", "source", "source_updated_at"
            );

            rejectedWriter.write(
                "record_number,source_id,name,facility_type,address,latitude,longitude,"
                    + "purpose,camera_count,source,source_updated_at,reject_reason"
            );
            rejectedWriter.newLine();

            List<PendingRow> batch = new ArrayList<>(batchSize);
            String record;

            while ((record = readCsvRecord(reader)) != null) {
                if (record.isBlank()) {
                    continue;
                }

                read++;

                try {
                    List<String> values = parseCsvRecord(record);
                    Row row = row(values, indexes);
                    batch.add(new PendingRow(read, row));

                    if (batch.size() >= batchSize) {
                        BatchResult result = flushBatch(batch, rejectedWriter);
                        succeeded += result.succeeded();
                        errors += result.errors();
                        batch.clear();
                    }
                } catch (Exception error) {
                    errors++;
                    writeRejected(
                        rejectedWriter,
                        read,
                        record,
                        indexes,
                        error
                    );
                }

                if (read % PROGRESS_INTERVAL == 0) {
                    printProgress(read, succeeded, errors, startedAt);
                }
            }

            if (!batch.isEmpty()) {
                BatchResult result = flushBatch(batch, rejectedWriter);
                succeeded += result.succeeded();
                errors += result.errors();
            }
        }

        long total = countFacilities();
        return new ImportResult(read, succeeded, errors, total, rejectedPool.toAbsolutePath().toString());
    }

    public void ensureSchema() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS safe_infrastructure (
              id BIGINT NOT NULL AUTO_INCREMENT,
              source_id VARCHAR(128) NOT NULL,
              area_id BIGINT NULL,
              facility_type VARCHAR(30) NOT NULL,
              name VARCHAR(120) NOT NULL,
              address VARCHAR(255) NOT NULL,
              latitude DOUBLE NULL,
              longitude DOUBLE NULL,
              geocode_status VARCHAR(20) NOT NULL DEFAULT 'NOT_ATTEMPTED',
              geocode_attempts INT NOT NULL DEFAULT 0,
              geocode_last_attempt_at DATETIME(6) NULL,
              geocode_error VARCHAR(255) NULL,
              purpose VARCHAR(50) NULL,
              camera_count INT NOT NULL DEFAULT 1,
              source_name VARCHAR(120) NOT NULL,
              source_updated_at DATE NOT NULL,
              created_at DATETIME(6) NOT NULL,
              updated_at DATETIME(6) NOT NULL,
              PRIMARY KEY (id),
              UNIQUE KEY uk_safe_infra_source_id (source_id),
              KEY idx_safe_infra_type (facility_type),
              KEY idx_safe_infra_lat_lng (latitude, longitude),
              KEY idx_safe_infra_geocode_status (facility_type, geocode_status, id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
    }

    private BatchResult flushBatch(List<PendingRow> pendingRows, BufferedWriter rejectedWriter)
        throws IOException {

        List<Row> rows = pendingRows.stream().map(PendingRow::row).toList();

        try {
            jdbc.batchUpdate(
                UPSERT_SQL,
                rows,
                rows.size(),
                (statement, row) -> bind(statement, row)
            );
            return new BatchResult(rows.size(), 0);
        } catch (DataAccessException batchError) {
            // One bad row should not hide the rest of the batch.
            // Re-run this batch row-by-row only when a batch fails, so the exact bad row is logged.
            int succeeded = 0;
            int errors = 0;

            for (PendingRow pending : pendingRows) {
                try {
                    upsertOne(pending.row());
                    succeeded++;
                } catch (Exception rowError) {
                    errors++;
                    writeRejected(
                        rejectedWriter,
                        pending.recordNumber(),
                        pending.row(),
                        rowError
                    );
                }
            }

            return new BatchResult(succeeded, errors);
        }
    }

    private int upsertOne(Row row) {
        return jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(UPSERT_SQL);
            bind(statement, row);
            return statement;
        });
    }

    private static void bind(PreparedStatement statement, Row row) throws SQLException {
        statement.setString(1, row.sourceId());
        statement.setString(2, row.facilityType());
        statement.setString(3, row.name());
        statement.setString(4, row.address());
        setDouble(statement, 5, row.latitude());
        setDouble(statement, 6, row.longitude());
        statement.setString(7, row.purpose());
        statement.setInt(8, row.cameraCount());
        statement.setString(9, row.source());
        statement.setDate(10, Date.valueOf(row.sourceUpdatedAt()));
    }

    private long countFacilities() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM safe_infrastructure", Long.class);
        return count == null ? 0 : count;
    }

    private static Row row(List<String> values, Map<String, Integer> indexes) {
        String sourceId = requiredValue(values, indexes, "source_id");
        String name = requiredValue(values, indexes, "name");
        String facilityType = requiredValue(values, indexes, "facility_type");
        String address = requiredValue(values, indexes, "address");
        String source = requiredValue(values, indexes, "source");
        String sourceDateText = requiredValue(values, indexes, "source_updated_at");

        validateLength("source_id", sourceId, 128);
        validateLength("facility_type", facilityType, 30);
        validateLength("name", name, 120);
        validateLength("address", address, 255);
        validateLength("source", source, 120);

        String purpose = optionalValue(values, indexes, "purpose");
        validateLength("purpose", purpose, 50);

        LocalDate sourceUpdatedAt = LocalDate.parse(sourceDateText);

        Double latitude = nullableDouble(value(values, indexes, "latitude"));
        Double longitude = nullableDouble(value(values, indexes, "longitude"));

        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException(
                "latitude and longitude must both be present or both be blank"
            );
        }
        boolean policeFacility = "POLICE_BOX".equals(facilityType)
            || "POLICE_STATION".equals(facilityType);
        if (latitude == null && !policeFacility) {
            throw new IllegalArgumentException(
                "usable coordinates are required for non-police facilities"
            );
        }
        if (latitude != null && (!Double.isFinite(latitude) || latitude < -90 || latitude > 90)) {
            throw new IllegalArgumentException("latitude is outside WGS84 range");
        }
        if (longitude != null && (!Double.isFinite(longitude) || longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("longitude is outside WGS84 range");
        }

        return new Row(
            sourceId,
            name,
            facilityType,
            address,
            latitude,
            longitude,
            purpose,
            optionalInt(values, indexes, "camera_count", 1),
            source,
            sourceUpdatedAt
        );
    }

    private static String readCsvRecord(BufferedReader reader) throws IOException {
        String firstLine = reader.readLine();
        if (firstLine == null) {
            return null;
        }

        StringBuilder record = new StringBuilder(firstLine);

        while (!isCompleteCsvRecord(record)) {
            String nextLine = reader.readLine();
            if (nextLine == null) {
                throw new IllegalArgumentException("CSV ended inside a quoted field.");
            }
            record.append('\n').append(nextLine);
        }

        return record.toString();
    }

    private static boolean isCompleteCsvRecord(CharSequence record) {
        boolean quoted = false;

        for (int i = 0; i < record.length(); i++) {
            char ch = record.charAt(i);

            if (ch != '"') {
                continue;
            }

            if (quoted && i + 1 < record.length() && record.charAt(i + 1) == '"') {
                i++;
                continue;
            }

            quoted = !quoted;
        }

        return !quoted;
    }

    private static List<String> parseCsvRecord(String record) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < record.length(); i++) {
            char ch = record.charAt(i);

            if (ch == '"') {
                if (quoted && i + 1 < record.length() && record.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(ch);
            }
        }

        if (quoted) {
            throw new IllegalArgumentException("Unclosed quoted CSV field.");
        }

        values.add(value.toString());
        return values;
    }

    private static Map<String, Integer> indexes(List<String> header) {
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            result.put(header.get(i), i);
        }
        return result;
    }

    private static void require(Map<String, Integer> indexes, String... columns) {
        for (String column : columns) {
            if (!indexes.containsKey(column)) {
                throw new IllegalArgumentException("CSV column missing: " + column);
            }
        }
    }

    private static String requiredValue(
        List<String> values,
        Map<String, Integer> indexes,
        String column
    ) {
        String text = value(values, indexes, column);
        if (text.isBlank()) {
            throw new IllegalArgumentException(column + " is blank");
        }
        return text;
    }

    private static String value(
        List<String> values,
        Map<String, Integer> indexes,
        String column
    ) {
        Integer index = indexes.get(column);
        if (index == null) {
            throw new IllegalArgumentException("CSV column missing: " + column);
        }
        return index < values.size() ? values.get(index).trim() : "";
    }

    private static String optionalValue(
        List<String> values,
        Map<String, Integer> indexes,
        String column
    ) {
        Integer index = indexes.get(column);
        return index != null && index < values.size() ? values.get(index).trim() : "";
    }

    private static int optionalInt(
        List<String> values,
        Map<String, Integer> indexes,
        String column,
        int fallback
    ) {
        String text = optionalValue(values, indexes, column);
        if (text.isBlank()) {
            return fallback;
        }

        int parsed = Integer.parseInt(text);
        if (parsed < 1) {
            throw new IllegalArgumentException(column + " must be >= 1");
        }
        return parsed;
    }

    private static Double nullableDouble(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return Double.valueOf(text);
    }

    private static void validateLength(String column, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(
                column + " length " + value.length() + " exceeds DB limit " + maxLength
            );
        }
    }

    private static void setDouble(PreparedStatement statement, int index, Double value)
        throws SQLException {

        if (value == null) {
            statement.setNull(index, Types.DOUBLE);
        } else {
            statement.setDouble(index, value);
        }
    }

    private static String stripBom(String line) {
        if (line == null) {
            throw new IllegalArgumentException("CSV is empty.");
        }
        return line.startsWith("\uFEFF") ? line.substring(1) : line;
    }

    private static int positiveIntEnv(String name, int fallback) {
        String text = System.getenv(name);
        if (text == null || text.isBlank()) {
            return fallback;
        }

        int parsed = Integer.parseInt(text);
        if (parsed < 1) {
            throw new IllegalArgumentException(name + " must be >= 1");
        }
        return parsed;
    }

    private static Path rejectedPoolPath(Path csvPath) {
        String configured = System.getenv("SAFETY_IMPORT_REJECTED_CSV");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }

        Path projectRoot = Path.of(".").toAbsolutePath().normalize();
        return projectRoot
            .resolve("data")
            .resolve("rejected")
            .resolve("rejected_safety_infrastructure.csv");
    }

    private static void writeRejected(
        BufferedWriter writer,
        int recordNumber,
        String record,
        Map<String, Integer> indexes,
        Throwable error
    ) throws IOException {

        List<String> values;
        try {
            values = parseCsvRecord(record);
        } catch (Exception parseError) {
            values = List.of();
        }

        writeRejectedFields(
            writer,
            recordNumber,
            safeValue(values, indexes, "source_id"),
            safeValue(values, indexes, "name"),
            safeValue(values, indexes, "facility_type"),
            safeValue(values, indexes, "address"),
            safeValue(values, indexes, "latitude"),
            safeValue(values, indexes, "longitude"),
            safeValue(values, indexes, "purpose"),
            safeValue(values, indexes, "camera_count"),
            safeValue(values, indexes, "source"),
            safeValue(values, indexes, "source_updated_at"),
            rootMessage(error)
        );
    }

    private static void writeRejected(
        BufferedWriter writer,
        int recordNumber,
        Row row,
        Throwable error
    ) throws IOException {

        writeRejectedFields(
            writer,
            recordNumber,
            row.sourceId(),
            row.name(),
            row.facilityType(),
            row.address(),
            row.latitude() == null ? "" : row.latitude().toString(),
            row.longitude() == null ? "" : row.longitude().toString(),
            row.purpose(),
            Integer.toString(row.cameraCount()),
            row.source(),
            row.sourceUpdatedAt().toString(),
            rootMessage(error)
        );
    }

    private static void writeRejectedFields(
        BufferedWriter writer,
        int recordNumber,
        String sourceId,
        String name,
        String facilityType,
        String address,
        String latitude,
        String longitude,
        String purpose,
        String cameraCount,
        String source,
        String sourceUpdatedAt,
        String reason
    ) throws IOException {

        String[] values = {
            Integer.toString(recordNumber),
            sourceId,
            name,
            facilityType,
            address,
            latitude,
            longitude,
            purpose,
            cameraCount,
            source,
            sourceUpdatedAt,
            reason
        };

        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(csv(values[i]));
        }
        writer.newLine();
        writer.flush();
    }

    private static String safeValue(
        List<String> values,
        Map<String, Integer> indexes,
        String column
    ) {
        try {
            Integer index = indexes.get(column);
            if (index == null || index >= values.size()) {
                return "";
            }
            return values.get(index).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        String message = null;

        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getClass().getSimpleName() + ": " + current.getMessage();
            }
            current = current.getCause();
        }

        return message == null ? error.getClass().getSimpleName() : message;
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static void printProgress(
        int read,
        int succeeded,
        int errors,
        Instant startedAt
    ) {
        long seconds = Math.max(1L, Duration.between(startedAt, Instant.now()).toSeconds());
        long rowsPerSecond = read / seconds;

        System.out.printf(
            "Safety import progress: read=%d succeeded=%d rejected=%d speed=%d rows/s%n",
            read,
            succeeded,
            errors,
            rowsPerSecond
        );
    }

    private record PendingRow(int recordNumber, Row row) {
    }

    private record BatchResult(int succeeded, int errors) {
    }

    private record Row(
        String sourceId,
        String name,
        String facilityType,
        String address,
        Double latitude,
        Double longitude,
        String purpose,
        int cameraCount,
        String source,
        LocalDate sourceUpdatedAt
    ) {
    }

    public record ImportResult(
        int readRows,
        int succeededRows,
        int rejectedRows,
        long totalRows,
        String rejectedPoolPath
    ) {
    }
}
