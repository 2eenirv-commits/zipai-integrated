package com.onrender.zipai.dto.lifestyle;

import java.util.List;

public record LifestyleRecommendResponse(
        List<LifestyleRecommendationItem> items,
        LifestyleWeightResponse appliedWeights,
        String scope,
        String notice) {
}
