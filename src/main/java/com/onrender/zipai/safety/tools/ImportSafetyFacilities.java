package com.onrender.zipai.safety.tools;

import com.onrender.zipai.safety.service.SafetyFacilityImportService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class ImportSafetyFacilities {
    private ImportSafetyFacilities() {
    }

    public static void main(String[] args) throws Exception {
        String csvPathText = required("SAFETY_IMPORT_CSV");
        Path csvPath = Path.of(csvPathText);

        if (!Files.isRegularFile(csvPath)) {
            throw new IllegalArgumentException(
                "SAFETY_IMPORT_CSV does not point to a readable file: "
                    + csvPath.toAbsolutePath()
            );
        }

        Instant startedAt = Instant.now();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        SafetyFacilityImportService service = new SafetyFacilityImportService(jdbc);

        SafetyFacilityImportService.ImportResult result = service.importCsv(csvPath);

        long elapsedSeconds = Duration.between(startedAt, Instant.now()).toSeconds();

        System.out.println();
        System.out.println("Safety facility import completed.");
        System.out.println("CSV logical rows read: " + result.readRows());
        System.out.println("Successful rows: " + result.succeededRows());
        System.out.println("Rejected rows: " + result.rejectedRows());
        System.out.println("Total DB rows: " + result.totalRows());
        System.out.println("Rejected pool: " + result.rejectedPoolPath());
        System.out.println("Elapsed seconds: " + elapsedSeconds);

        if (result.rejectedRows() > 0) {
            System.out.println();
            System.out.println(
                "Some rows were rejected from the service table. Review the rejected pool above."
            );
        }
    }

    private static DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(withBatchRewrite(required("DB_URL")));
        dataSource.setUsername(required("DB_USERNAME"));
        dataSource.setPassword(required("DB_PASSWORD"));
        return dataSource;
    }

    static String withBatchRewrite(String url) {
        if (url.matches("(?i).*([?&])rewriteBatchedStatements=[^&]*.*")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "rewriteBatchedStatements=true";
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " environment variable is required.");
        }
        return value;
    }
}
