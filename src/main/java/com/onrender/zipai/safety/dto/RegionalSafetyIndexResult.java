package com.onrender.zipai.safety.dto;

public record RegionalSafetyIndexResult(
    boolean success,
    boolean available,
    Integer year,
    String sidoName,
    String sigunguName,
    RegionalSafetyIndex regionalSafety,
    String message
) {
}
