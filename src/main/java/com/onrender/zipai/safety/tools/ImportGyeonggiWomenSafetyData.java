package com.onrender.zipai.safety.tools;

import com.onrender.zipai.safety.service.WomenSafetyFacilityImportService;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class ImportGyeonggiWomenSafetyData {
    private ImportGyeonggiWomenSafetyData() {
    }

    public static void main(String[] args) throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(required("DB_URL"));
        dataSource.setUsername(required("DB_USERNAME"));
        dataSource.setPassword(required("DB_PASSWORD"));
        var result = new WomenSafetyFacilityImportService(new JdbcTemplate(dataSource))
            .importCsv(Path.of(required("GYEONGGI_WOMEN_SAFETY_IMPORT_CSV")));
        System.out.printf("read=%d succeeded=%d errors=%d total=%d searchable=%d%n",
            result.readRows(), result.succeededRows(), result.errorRows(), result.totalRows(), result.searchableRows());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " environment variable is required.");
        return value;
    }
}
