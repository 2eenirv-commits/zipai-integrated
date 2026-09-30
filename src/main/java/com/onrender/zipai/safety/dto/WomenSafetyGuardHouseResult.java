package com.onrender.zipai.safety.dto;

import java.time.LocalDate;
import java.util.List;

public record WomenSafetyGuardHouseResult(
    boolean success,
    boolean available,
    String coverageRegion,
    String coordinateValidation,
    int radiusMeters,
    int count,
    List<WomenSafetyGuardHouse> data,
    String source,
    LocalDate sourceUpdatedAt,
    String message,
    String notice
) {
}
