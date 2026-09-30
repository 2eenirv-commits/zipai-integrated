package com.onrender.zipai.safety.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record SafetyScoreResult(
    boolean success,
    SafetyLocation location,
    int radiusMeters,
    int score,
    String grade,
    String description,
    List<SafetyMetric> metrics,
    Map<String, Long> summary,
    List<SafetyFacility> facilities,
    String dataSource,
    LocalDate dataUpdatedAt
) {
}
