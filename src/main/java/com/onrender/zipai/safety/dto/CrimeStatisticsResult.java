package com.onrender.zipai.safety.dto;

import java.time.LocalDate;
import java.util.List;

public record CrimeStatisticsResult(
    boolean success,
    boolean available,
    String coverageType,
    String policeAgency,
    boolean provisional,
    String coverageNote,
    Integer year,
    String sidoName,
    String sigunguName,
    List<PoliceStationCrimeStatistic> stations,
    List<CrimeTypeStatistic> totals,
    String source,
    LocalDate sourceUpdatedAt,
    String message,
    String notice
) {
}
