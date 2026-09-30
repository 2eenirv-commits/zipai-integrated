package com.onrender.zipai.dto.lifestyle;

import com.onrender.zipai.domain.LifestyleArea;

public record LifestyleAreaResponse(
        Long areaId,
        String areaCode,
        String sido,
        String sigungu,
        String dong,
        String areaName) {

    public static LifestyleAreaResponse from(LifestyleArea area) {
        String name = String.join(" ",
                safe(area.getSido()),
                safe(area.getSigungu()),
                safe(area.getDong())).trim().replaceAll("\\s+", " ");
        return new LifestyleAreaResponse(
                area.getAreaId(), area.getAreaCode(), area.getSido(),
                area.getSigungu(), area.getDong(), name);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
