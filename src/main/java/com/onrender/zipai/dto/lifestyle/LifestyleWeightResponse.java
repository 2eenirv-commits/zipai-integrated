package com.onrender.zipai.dto.lifestyle;

public record LifestyleWeightResponse(
        int transport,
        int convenience,
        int medical,
        int education,
        int park,
        int safety,
        int commercial,
        int quiet,
        int cost) {
}
