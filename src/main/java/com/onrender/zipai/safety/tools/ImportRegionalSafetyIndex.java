package com.onrender.zipai.safety.tools;

import com.onrender.zipai.safety.service.RegionalSafetyIndexImportService;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class ImportRegionalSafetyIndex {
    private ImportRegionalSafetyIndex() {
    }

    public static void main(String[] args) throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        RegionalSafetyIndexImportService service = new RegionalSafetyIndexImportService(jdbc);
        RegionalSafetyIndexImportService.ImportResult result = service.importCsv(
            Path.of(required("REGIONAL_SAFETY_INDEX_IMPORT_CSV"))
        );
        System.out.println("Regional safety index import completed.");
        System.out.println("CSV read rows: " + result.readRows());
        System.out.println("Succeeded rows: " + result.succeededRows());
        System.out.println("Error rows: " + result.errorRows());
        System.out.println("Total DB rows: " + result.totalRows());
    }

    private static DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(required("DB_URL"));
        dataSource.setUsername(required("DB_USERNAME"));
        dataSource.setPassword(required("DB_PASSWORD"));
        return dataSource;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " environment variable is required.");
        }
        return value;
    }
}
