package com.onrender.zipai.safety.tools;

import com.onrender.zipai.safety.service.PoliceCrimeStatisticsImportService;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class ImportGyeonggiCrimeStatistics {
    private ImportGyeonggiCrimeStatistics() {
    }

    public static void main(String[] args) throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(required("DB_URL"));
        dataSource.setUsername(required("DB_USERNAME"));
        dataSource.setPassword(required("DB_PASSWORD"));
        var result = new PoliceCrimeStatisticsImportService(new JdbcTemplate(dataSource))
            .importCsv(Path.of(required("GYEONGGI_CRIME_IMPORT_CSV")));
        System.out.printf("read=%d succeeded=%d errors=%d total=%d%n",
            result.readRows(), result.succeededRows(), result.errorRows(), result.totalRows());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " environment variable is required.");
        return value;
    }
}
