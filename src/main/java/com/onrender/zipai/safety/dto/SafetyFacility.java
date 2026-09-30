package com.onrender.zipai.safety.dto;

import java.time.LocalDate;

public record SafetyFacility(
    Long id,
    String sourceId,
    String facilityType,
    String label,
    String name,
    String address,
    double latitude,
    double longitude,
    String purpose,
    int cameraCount,
    long distanceMeters,
    String sourceName,
    LocalDate sourceUpdatedAt
) {
}
