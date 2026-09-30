package com.onrender.zipai.safety.controller;

import com.onrender.zipai.safety.dto.SafetyFacility;
import com.onrender.zipai.safety.dto.SafetyLocation;
import com.onrender.zipai.safety.dto.SafetyScoreResult;
import com.onrender.zipai.safety.dto.RegionalSafetyIndexResult;
import com.onrender.zipai.safety.dto.CrimeStatisticsResult;
import com.onrender.zipai.safety.dto.WomenSafetyGuardHouseResult;
import com.onrender.zipai.safety.dto.WomenSafetyFacilityResult;
import com.onrender.zipai.safety.service.RegionalSafetyService;
import com.onrender.zipai.safety.service.SeoulSafetyInformationService;
import com.onrender.zipai.safety.service.SafetyService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/safe", "/api/safety"})
public class SafeController {
    private final SafetyService safetyService;
    private final RegionalSafetyService regionalSafetyService;
    private final SeoulSafetyInformationService seoulSafetyInformationService;

    public SafeController(
        SafetyService safetyService,
        RegionalSafetyService regionalSafetyService,
        SeoulSafetyInformationService seoulSafetyInformationService
    ) {
        this.safetyService = safetyService;
        this.regionalSafetyService = regionalSafetyService;
        this.seoulSafetyInformationService = seoulSafetyInformationService;
    }

    @GetMapping("/geocode")
    Map<String, Object> geocode(@RequestParam String query) {
        return locationResponse(query, safetyService.search(query));
    }

    @GetMapping("/search")
    Map<String, Object> search(
        @RequestParam(name = "q", required = false) String q,
        @RequestParam(name = "query", required = false) String query
    ) {
        String keyword = q != null ? q : query;
        return locationResponse(keyword, safetyService.search(keyword));
    }

    private static Map<String, Object> locationResponse(String query, List<SafetyLocation> locations) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", !locations.isEmpty());
        response.put("query", query);
        response.put("location", locations.isEmpty() ? null : locations.get(0));
        response.put("candidates", locations);
        return response;
    }

    @GetMapping("/infrastructure")
    Map<String, Object> infrastructure(
        @RequestParam(required = false) Double lat,
        @RequestParam(required = false) Double lng,
        @RequestParam(required = false) Integer radius,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String features
    ) {
        List<SafetyFacility> facilities = safetyService.facilities(lat, lng, radius, query, features);
        return Map.of("success", true, "count", facilities.size(), "facilities", facilities);
    }

    @GetMapping("/score")
    SafetyScoreResult score(
        @RequestParam(required = false) Double lat,
        @RequestParam(required = false) Double lng,
        @RequestParam(required = false) Integer radius,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String features
    ) {
        return safetyService.score(lat, lng, radius, query, features);
    }

    @GetMapping("/regional")
    RegionalSafetyIndexResult regional(
        @RequestParam(required = false) Integer year,
        @RequestParam String sido,
        @RequestParam(required = false) String sigungu
    ) {
        return regionalSafetyService.lookup(year, sido, sigungu);
    }

    @GetMapping("/crime-statistics")
    CrimeStatisticsResult crimeStatistics(
        @RequestParam String sido,
        @RequestParam(required = false) String sigungu,
        @RequestParam(required = false) Integer year
    ) {
        return seoulSafetyInformationService.crimeStatistics(sido, sigungu, year);
    }

    @GetMapping("/women-safe-houses")
    WomenSafetyGuardHouseResult womenSafeHouses(
        @RequestParam Double lat,
        @RequestParam Double lng,
        @RequestParam(required = false) Integer radius,
        @RequestParam(required = false) String sido
    ) {
        return seoulSafetyInformationService.womenSafeHouses(lat, lng, radius, sido);
    }

    @GetMapping("/women-safety-facilities")
    WomenSafetyFacilityResult womenSafetyFacilities(
        @RequestParam Double lat,
        @RequestParam Double lng,
        @RequestParam(required = false) Integer radius,
        @RequestParam(required = false) String sido,
        @RequestParam(required = false) String sigungu
    ) {
        return seoulSafetyInformationService.womenSafetyFacilities(lat, lng, radius, sido, sigungu);
    }
}
