package com.onrender.zipai.safety.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onrender.zipai.safety.dto.SafetyFacility;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SafetyScoreServiceTest {

    private final SafetyScoreService service = new SafetyScoreService();

    @Test
    void fullTargetDensityProduces100() {
        List<SafetyFacility> facilities = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            facilities.add(facility("CCTV", "생활방범", 1));
        }
        for (int i = 0; i < 2; i++) {
            facilities.add(facility("POLICE_BOX", null, 1));
        }
        for (int i = 0; i < 6; i++) {
            facilities.add(facility("SAFETY_BELL", null, 1));
        }
        for (int i = 0; i < 60; i++) {
            facilities.add(facility("STREET_LIGHT", null, 1));
        }

        SafetyScoreService.Score score = service.calculate(facilities, 500, true);

        assertEquals(100, score.value());
        assertEquals("매우 안전", score.grade());
        assertEquals(3, score.metrics().size());
    }

    @Test
    void weightPolicyIs40_35_25() {
        List<SafetyFacility> facilities = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            facilities.add(facility("CCTV", "생활방범", 1));
        }

        SafetyScoreService.Score score = service.calculate(facilities, 500, true);

        // CCTV 100, police 0, safety facilities 0:
        // 100 * 0.40 + 0 * 0.35 + 0 * 0.25 = 40
        assertEquals(40, score.value());
    }

    @Test
    void safetyFacilityInternalWeightIs70_30() {
        List<SafetyFacility> facilities = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            facilities.add(facility("SAFETY_BELL", null, 1));
        }

        SafetyScoreService.Score score = service.calculate(facilities, 500, true);

        // Bell score 100, light score 0 => safety component 70.
        // Overall = 70 * 0.25 = 17.5 => 18.
        assertEquals(18, score.value());
        assertTrue(score.metrics().stream()
            .anyMatch(metric -> metric.name().equals("안전시설") && metric.value() == 70));
    }

    private static SafetyFacility facility(String type, String purpose, int cameraCount) {
        return new SafetyFacility(
            1L,
            "test-" + type + "-" + System.nanoTime(),
            type,
            type,
            "test",
            "test address",
            37.5,
            127.0,
            purpose,
            cameraCount,
            100,
            "test",
            LocalDate.of(2026, 1, 1)
        );
    }
}
