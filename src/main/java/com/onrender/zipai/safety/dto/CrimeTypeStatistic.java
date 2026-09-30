package com.onrender.zipai.safety.dto;

public record CrimeTypeStatistic(
    String crimeType,
    int occurrenceCount,
    int arrestCount
) {
}
