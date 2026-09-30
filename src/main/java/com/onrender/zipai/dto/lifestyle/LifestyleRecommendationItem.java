package com.onrender.zipai.dto.lifestyle;

import java.time.LocalDate;
import java.util.List;

public record LifestyleRecommendationItem(
        int rank,
        Long areaId,
        String areaCode,
        String sido,
        String sigungu,
        String dong,
        String areaName,
        double totalScore,
        String matchLevel,
        LifestyleScoreDetail scores,
        List<String> reasons,
        String sourceName,
        LocalDate sourceDate) {
}
