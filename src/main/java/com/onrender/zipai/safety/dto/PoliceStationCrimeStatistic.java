package com.onrender.zipai.safety.dto;

import java.util.List;

public record PoliceStationCrimeStatistic(
    String policeStationName,
    String sigunguName,
    List<CrimeTypeStatistic> statistics
) {
}
