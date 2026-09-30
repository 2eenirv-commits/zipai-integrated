package com.onrender.zipai.safety.dto;

import java.time.LocalDate;

public record WomenSafetyFacility(
    long id,
    String sourceId,
    String sidoName,
    String sigunguName,
    String facilityType,
    String name,
    String brandName,
    String address,
    double latitude,
    double longitude,
    long distanceMeters,
    String sourceName,
    LocalDate sourceUpdatedAt,
    Integer dataYear,
    String coverageNote
) {
}
