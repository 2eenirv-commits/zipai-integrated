package com.onrender.zipai.safety.dto;

import java.time.LocalDate;

public record WomenSafetyGuardHouse(
    long id,
    String sourceId,
    String brandName,
    String storeName,
    String sidoName,
    String sigunguName,
    String address,
    double latitude,
    double longitude,
    long distanceMeters,
    String sourceName,
    LocalDate sourceUpdatedAt
) {
}
