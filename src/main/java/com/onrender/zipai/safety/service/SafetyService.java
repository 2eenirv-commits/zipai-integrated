package com.onrender.zipai.safety.service;

import com.onrender.zipai.safety.dto.SafetyFacility;
import com.onrender.zipai.safety.dto.SafetyLocation;
import com.onrender.zipai.safety.dto.SafetyScoreResult;
import com.onrender.zipai.safety.repository.SafetyRepository;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class SafetyService {
    private static final long FACILITY_TYPE_CACHE_TTL_MILLIS = 5 * 60 * 1000L;

    private final GeocodingService geocodingService;
    private final SafetyRepository safetyRepository;
    private final SafetyScoreService scoreService;
    private final SafetyProperties properties;
    private volatile Boolean streetLightDataAvailable;
    private volatile long streetLightDataAvailableCheckedAt;

    public SafetyService(
        GeocodingService geocodingService,
        SafetyRepository safetyRepository,
        SafetyScoreService scoreService,
        SafetyProperties properties
    ) {
        this.geocodingService = geocodingService;
        this.safetyRepository = safetyRepository;
        this.scoreService = scoreService;
        this.properties = properties;
    }

    public List<SafetyLocation> search(String query) {
        return geocodingService.search(query);
    }

    public List<SafetyFacility> facilities(Double lat, Double lng, Integer radius, String query) {
        return facilities(lat, lng, radius, query, null);
    }

    public List<SafetyFacility> facilities(Double lat, Double lng, Integer radius, String query, String features) {
        int radiusMeters = radius(radius);
        Set<String> selectedFeatures = parseFeatures(features);
        SafetyLocation location = resolveLocation(lat, lng, query);
        return filterFacilities(
            safetyRepository.findFacilities(location.latitude(), location.longitude(), radiusMeters),
            selectedFeatures
        );
    }

    public SafetyScoreResult score(Double lat, Double lng, Integer radius, String query) {
        return score(lat, lng, radius, query, null);
    }

    public SafetyScoreResult score(Double lat, Double lng, Integer radius, String query, String features) {
        int radiusMeters = radius(radius);
        Set<String> selectedFeatures = parseFeatures(features);
        SafetyLocation location = resolveLocation(lat, lng, query);
        List<SafetyFacility> facilities = filterFacilities(
            safetyRepository.findFacilities(location.latitude(), location.longitude(), radiusMeters),
            selectedFeatures
        );
        SafetyScoreService.Score score = scoreService.calculate(
            facilities,
            radiusMeters,
            streetLightDataAvailable(),
            selectedFeatures
        );
        LocalDate dataUpdatedAt = facilities.stream()
            .map(SafetyFacility::sourceUpdatedAt)
            .filter(java.util.Objects::nonNull)
            .max(LocalDate::compareTo)
            .orElse(null);
        return new SafetyScoreResult(
            true,
            location,
            radiusMeters,
            score.value(),
            score.grade(),
            score.description(),
            score.metrics(),
            score.summary(),
            facilities,
            lat != null && lng != null
                ? "공공데이터 적재 DB + 입력 좌표 기반 분석"
                : "공공데이터 적재 DB + VWorld 주소검색",
            dataUpdatedAt
        );
    }

    private static Set<String> parseFeatures(String rawFeatures) {
        if (rawFeatures == null) {
            return SafetyScoreService.ALL_FEATURES;
        }
        Set<String> selected = new LinkedHashSet<>();
        for (String token : rawFeatures.split(",")) {
            String feature = normalizeFeature(token);
            if (!feature.isBlank()) {
                selected.add(feature);
            }
        }
        if (selected.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "분석 항목을 하나 이상 선택해 주세요.");
        }
        return selected;
    }

    private static String normalizeFeature(String token) {
        String value = token == null ? "" : token.trim().toLowerCase().replace("_", "-");
        return switch (value) {
            case "" -> "";
            case "cctv" -> SafetyScoreService.FEATURE_CCTV;
            case "police", "police-facility", "police-facilities" -> SafetyScoreService.FEATURE_POLICE;
            case "safety", "safety-facility", "safety-facilities", "safety-bell", "street-light" -> SafetyScoreService.FEATURE_SAFETY;
            default -> throw new ResponseStatusException(BAD_REQUEST, "지원하지 않는 분석 항목입니다: " + token);
        };
    }

    private static List<SafetyFacility> filterFacilities(List<SafetyFacility> facilities, Set<String> selectedFeatures) {
        return facilities.stream()
            .filter(facility -> isSelectedFacility(facility.facilityType(), selectedFeatures))
            .toList();
    }

    private static boolean isSelectedFacility(String facilityType, Set<String> selectedFeatures) {
        return switch (facilityType) {
            case "CCTV" -> selectedFeatures.contains(SafetyScoreService.FEATURE_CCTV);
            case "POLICE_STATION", "POLICE_BOX" -> selectedFeatures.contains(SafetyScoreService.FEATURE_POLICE);
            case "SAFETY_BELL", "STREET_LIGHT" -> selectedFeatures.contains(SafetyScoreService.FEATURE_SAFETY);
            default -> false;
        };
    }

    private SafetyLocation resolveLocation(Double lat, Double lng, String query) {
        if (lat != null || lng != null) {
            validateCoordinate(lat, lng);
            return new SafetyLocation(null, "선택 위치", "사용자 지정 좌표", lat, lng, "사용자 입력", java.time.LocalDate.now());
        }
        return geocodingService.first(query);
    }

    private int radius(Integer radius) {
        int value = radius == null ? properties.defaultRadiusMeters() : radius;
        if (value < 100 || value > properties.maxRadiusMeters()) {
            throw new ResponseStatusException(BAD_REQUEST, "분석 반경은 100m 이상 " + properties.maxRadiusMeters() + "m 이하로 입력해 주세요.");
        }
        return value;
    }

    private boolean streetLightDataAvailable() {
        long now = System.currentTimeMillis();
        Boolean cached = streetLightDataAvailable;
        if (cached != null && now - streetLightDataAvailableCheckedAt < FACILITY_TYPE_CACHE_TTL_MILLIS) {
            return cached;
        }
        boolean available = safetyRepository.existsFacilityType("STREET_LIGHT");
        streetLightDataAvailable = available;
        streetLightDataAvailableCheckedAt = now;
        return available;
    }

    private static void validateCoordinate(Double lat, Double lng) {
        if (lat == null || lng == null) {
            throw new ResponseStatusException(BAD_REQUEST, "위도와 경도를 함께 입력해 주세요.");
        }
        if (!Double.isFinite(lat) || lat < -90 || lat > 90) {
            throw new ResponseStatusException(BAD_REQUEST, "위도는 -90 이상 90 이하로 입력해 주세요.");
        }
        if (!Double.isFinite(lng) || lng < -180 || lng > 180) {
            throw new ResponseStatusException(BAD_REQUEST, "경도는 -180 이상 180 이하로 입력해 주세요.");
        }
    }
}
