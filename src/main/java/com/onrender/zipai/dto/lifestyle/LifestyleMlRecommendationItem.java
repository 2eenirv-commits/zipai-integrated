package com.onrender.zipai.dto.lifestyle;

public record LifestyleMlRecommendationItem(
        int rank,
        Long areaId,
        String areaCode,
        String sido,
        String sigungu,
        double score,
        Double distance,
        String reason) {
}
