package com.onrender.zipai.safety.dto;

import java.time.LocalDate;

public record SafetyLocation(
    Long id,
    String name,
    String address,
    double latitude,
    double longitude,
    String sourceName,
    LocalDate sourceUpdatedAt,
    String sidoName,
    String sigunguName
) {
    public SafetyLocation(
        Long id,
        String name,
        String address,
        double latitude,
        double longitude,
        String sourceName,
        LocalDate sourceUpdatedAt
    ) {
        this(id, name, address, latitude, longitude, sourceName, sourceUpdatedAt, null, null);
    }
}
