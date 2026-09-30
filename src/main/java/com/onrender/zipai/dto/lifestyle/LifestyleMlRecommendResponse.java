package com.onrender.zipai.dto.lifestyle;

import java.util.List;
import java.util.Map;

public record LifestyleMlRecommendResponse(
        int areaCount,
        int featureCount,
        List<LifestyleMlAlgorithmResult> algorithms,
        LifestyleMlSelectedAreaResponse selectedArea,
        Map<String, Integer> overlapAtK,
        String notice) {
}
