package com.onrender.zipai.safety.dto;

import java.util.List;

public record WomenSafetyFacilityResult(
    boolean success,
    boolean available,
    String coverageRegion,
    List<String> supportedRegions,
    String coordinateValidation,
    int radiusMeters,
    int count,
    List<WomenSafetyFacility> data,
    String message,
    String notice
) {
}
