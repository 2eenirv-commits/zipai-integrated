package com.onrender.zipai.dto.lifestyle;

public record LifestyleScoreDetail(
        Integer transport,
        Integer convenience,
        Integer medical,
        Integer education,
        Integer park,
        Integer safety,
        Integer commercial,
        Integer quiet,
        Integer cost) {
}
