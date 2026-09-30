package com.onrender.zipai.safety.tools;

import com.onrender.zipai.safety.service.SeoulSafetyDataImportService;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class ImportSeoulSafetyData {
    private ImportSeoulSafetyData() {
    }

    public static void main(String[] args) throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        SeoulSafetyDataImportService service = new SeoulSafetyDataImportService(jdbc);
        var crime = service.importCrime(Path.of(required("SEOUL_CRIME_IMPORT_CSV")));
        var women = service.importGuardHouses(Path.of(required("WOMEN_SAFE_HOUSE_IMPORT_CSV")));
        System.out.printf("Crime: read=%d succeeded=%d errors=%d total=%d%n",
            crime.readRows(), crime.succeededRows(), crime.errorRows(), crime.totalRows());
        System.out.printf("Women safe houses: read=%d succeeded=%d errors=%d total=%d%n",
            women.readRows(), women.succeededRows(), women.errorRows(), women.totalRows());
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
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " environment variable is required.");
        return value;
    }
}
