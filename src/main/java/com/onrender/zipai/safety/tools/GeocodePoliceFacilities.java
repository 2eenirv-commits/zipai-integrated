package com.onrender.zipai.safety.tools;

import com.onrender.zipai.safety.service.PoliceFacilityGeocodeService;
import com.onrender.zipai.safety.service.VworldGeocodingClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class GeocodePoliceFacilities {
    private static final int DEFAULT_LIMIT = Integer.MAX_VALUE;
    private static final long DEFAULT_DELAY_MILLIS = 250L;

    private GeocodePoliceFacilities() {
    }

    public static void main(String[] args) {
        String apiKey = required("VWORLD_API_KEY");
        int limit = intOption(args, "--limit", DEFAULT_LIMIT);
        long delayMillis = longOption(args, "--delay-ms", DEFAULT_DELAY_MILLIS);
        boolean retryFailed = hasFlag(args, "--retry-failed");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        PoliceFacilityGeocodeService service = new PoliceFacilityGeocodeService(
            jdbc,
            new VworldGeocodingClient(apiKey)
        );
        PoliceFacilityGeocodeService.BatchResult result = service.geocodeMissingCoordinates(
            limit, delayMillis, retryFailed
        );
        System.out.println("Police facility geocoding completed.");
        System.out.println("Mode: " + (retryFailed ? "retry_failed" : "pending"));
        System.out.println("Targets: " + result.targets());
        System.out.println("Success: " + result.success());
        System.out.println("Failed: " + result.failed());
        System.out.println("Skipped: " + result.skipped());
        System.out.println("API calls: " + result.apiCalls());
        System.out.println("Remaining pending police coordinates: " + result.remainingTargets());
        System.out.println("Failed checkpoint police coordinates: " + result.failedTargets());
        System.out.println("Total null police coordinates: " + result.nullCoordinateTargets());
    }

    private static DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(required("DB_URL"));
        dataSource.setUsername(required("DB_USERNAME"));
        dataSource.setPassword(required("DB_PASSWORD"));
        return dataSource;
    }

    private static int intOption(String[] args, String name, int fallback) {
        String value = option(args, name);
        if (value == null || value.isBlank()) return fallback;
        return Math.max(1, Integer.parseInt(value));
    }

    private static long longOption(String[] args, String name, long fallback) {
        String value = option(args, name);
        if (value == null || value.isBlank()) return fallback;
        return Math.max(0, Long.parseLong(value));
    }

    private static String option(String[] args, String name) {
        String prefix = name + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) return arg.substring(prefix.length());
        }
        return null;
    }

    private static boolean hasFlag(String[] args, String name) {
        for (String arg : args) {
            if (name.equals(arg)) return true;
        }
        return false;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " environment variable is required.");
        }
        return value;
    }
}
