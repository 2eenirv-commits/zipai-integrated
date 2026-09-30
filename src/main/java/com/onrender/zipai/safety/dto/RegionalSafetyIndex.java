package com.onrender.zipai.safety.dto;

import java.time.LocalDate;

public record RegionalSafetyIndex(
    long id,
    int year,
    String regionLevel,
    String sidoName,
    String sigunguName,
    int trafficGrade,
    int fireGrade,
    int crimeGrade,
    int lifeSafetyGrade,
    int suicideGrade,
    int infectiousDiseaseGrade,
    String sourceName,
    LocalDate sourceUpdatedAt
) {
}
