package com.onrender.zipai.safety.service;

import com.onrender.zipai.safety.dto.SafetyFacility;
import com.onrender.zipai.safety.dto.SafetyMetric;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SafetyScoreService {
    private static final double CCTV_WEIGHT = 0.40;
    private static final double POLICE_WEIGHT = 0.35;
    private static final double SAFETY_FACILITY_WEIGHT = 0.25;
    private static final double SAFETY_BELL_WEIGHT = 0.70;
    private static final double STREET_LIGHT_WEIGHT = 0.30;
    private static final int CCTV_TARGET_500M = 8;
    private static final int POLICE_TARGET_500M = 2;
    private static final int SAFETY_BELL_TARGET_500M = 6;
    private static final Integer STREET_LIGHT_TARGET_500M = 60;
    public static final String FEATURE_CCTV = "cctv";
    public static final String FEATURE_POLICE = "police";
    public static final String FEATURE_SAFETY = "safety";
    public static final Set<String> ALL_FEATURES = Set.of(FEATURE_CCTV, FEATURE_POLICE, FEATURE_SAFETY);

    public Score calculate(List<SafetyFacility> facilities, int radiusMeters) {
        return calculate(facilities, radiusMeters, false, ALL_FEATURES);
    }

    public Score calculate(List<SafetyFacility> facilities, int radiusMeters, boolean streetLightDataAvailable) {
        return calculate(facilities, radiusMeters, streetLightDataAvailable, ALL_FEATURES);
    }

    public Score calculate(List<SafetyFacility> facilities, int radiusMeters, boolean streetLightDataAvailable, Set<String> selectedFeatures) {
        Map<String, Long> summary = facilities.stream()
            .collect(Collectors.groupingBy(SafetyFacility::facilityType, Collectors.counting()));
        int cctvScore = densityScore(cctvWeightedCount(facilities), radiusMeters, CCTV_TARGET_500M);
        int policeScore = densityScore(count(summary, "POLICE_STATION") + count(summary, "POLICE_BOX"), radiusMeters, POLICE_TARGET_500M);
        int safetyFacilityScore = safetyFacilityScore(summary, radiusMeters, streetLightDataAvailable);
        WeightedScore weightedScore = weightedScore(cctvScore, policeScore, safetyFacilityScore, selectedFeatures);
        int score = weightedScore.value();
        return new Score(
            score,
            grade(score),
            description(score),
            weightedScore.metrics(),
            summary
        );
    }

    private static WeightedScore weightedScore(int cctvScore, int policeScore, int safetyFacilityScore, Set<String> selectedFeatures) {
        double total = 0;
        double weight = 0;
        List<SafetyMetric> metrics = new ArrayList<>();
        if (selectedFeatures.contains(FEATURE_CCTV)) {
            total += cctvScore * CCTV_WEIGHT;
            weight += CCTV_WEIGHT;
            metrics.add(new SafetyMetric("CCTV", cctvScore));
        }
        if (selectedFeatures.contains(FEATURE_POLICE)) {
            total += policeScore * POLICE_WEIGHT;
            weight += POLICE_WEIGHT;
            metrics.add(new SafetyMetric("경찰·치안시설", policeScore));
        }
        if (selectedFeatures.contains(FEATURE_SAFETY)) {
            total += safetyFacilityScore * SAFETY_FACILITY_WEIGHT;
            weight += SAFETY_FACILITY_WEIGHT;
            metrics.add(new SafetyMetric("안전시설", safetyFacilityScore));
        }
        int score = weight == 0 ? 0 : clamp((int) Math.round(total / weight));
        return new WeightedScore(score, metrics);
    }

    private static int safetyFacilityScore(Map<String, Long> summary, int radiusMeters, boolean streetLightDataAvailable) {
        int safetyBellScore = densityScore(count(summary, "SAFETY_BELL"), radiusMeters, SAFETY_BELL_TARGET_500M);
        if (!streetLightDataAvailable || STREET_LIGHT_TARGET_500M == null) {
            return safetyBellScore;
        }
        int streetLightScore = densityScore(count(summary, "STREET_LIGHT"), radiusMeters, STREET_LIGHT_TARGET_500M);
        return clamp((int) Math.round(
            safetyBellScore * SAFETY_BELL_WEIGHT
                + streetLightScore * STREET_LIGHT_WEIGHT
        ));
    }

    private static int densityScore(double count, int radiusMeters, int targetCountAt500m) {
        double radiusFactor = Math.max(1.0, radiusMeters / 500.0);
        double target = targetCountAt500m * radiusFactor * radiusFactor;
        return clamp((int) Math.round(Math.min(1.0, count / target) * 100));
    }

    private static double cctvWeightedCount(List<SafetyFacility> facilities) {
        return facilities.stream()
            .filter(facility -> "CCTV".equals(facility.facilityType()))
            .mapToDouble(facility -> Math.max(1, facility.cameraCount()) * cctvPurposeWeight(facility.purpose()))
            .sum();
    }

    private static double cctvPurposeWeight(String purpose) {
        return switch (purpose == null ? "" : purpose) {
            case "생활방범" -> 1.0;
            case "다목적" -> 0.85;
            case "차량방범", "어린이보호" -> 0.7;
            case "기타" -> 0.4;
            case "교통단속", "교통정보수집" -> 0.25;
            case "시설물관리", "재난재해", "쓰레기단속" -> 0.2;
            default -> 0.5;
        };
    }

    private static long count(Map<String, Long> summary, String type) {
        return summary.getOrDefault(type, 0L);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String grade(int score) {
        if (score >= 90) return "매우 안전";
        if (score >= 75) return "안전";
        if (score >= 60) return "보통";
        if (score >= 40) return "주의";
        return "위험";
    }

    private static String description(int score) {
        if (score >= 90) return "공공 안전 인프라가 매우 충분한 지역입니다.";
        if (score >= 75) return "공공 안전 인프라가 비교적 충분한 지역입니다.";
        if (score >= 60) return "기본 공공 안전 인프라가 확인되는 지역입니다.";
        if (score >= 40) return "공공 안전 인프라가 다소 부족해 생활 동선 확인이 필요합니다.";
        return "공공 안전 인프라 데이터 기준으로 추가 확인이 필요한 지역입니다.";
    }

    public record Score(
        int value,
        String grade,
        String description,
        List<SafetyMetric> metrics,
        Map<String, Long> summary
    ) {
    }

    private record WeightedScore(int value, List<SafetyMetric> metrics) {
    }
}
