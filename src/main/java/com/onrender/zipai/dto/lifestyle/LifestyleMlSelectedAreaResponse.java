package com.onrender.zipai.dto.lifestyle;

import java.util.Map;

public record LifestyleMlSelectedAreaResponse(
        String sido,
        String sigungu,
        Map<String, LifestyleMlRecommendationItem> results,
        Map<String, Double> featureScores) {
}
