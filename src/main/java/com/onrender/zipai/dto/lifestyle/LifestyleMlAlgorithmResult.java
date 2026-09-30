package com.onrender.zipai.dto.lifestyle;

import java.util.List;

public record LifestyleMlAlgorithmResult(
        String algorithm,
        String label,
        List<LifestyleMlRecommendationItem> items) {
}
