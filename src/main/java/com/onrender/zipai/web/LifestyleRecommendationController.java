package com.onrender.zipai.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onrender.zipai.dto.lifestyle.ItemResponse;
import com.onrender.zipai.dto.lifestyle.ItemsResponse;
import com.onrender.zipai.dto.lifestyle.LifestyleAreaResponse;
import com.onrender.zipai.dto.lifestyle.LifestyleMlRecommendResponse;
import com.onrender.zipai.dto.lifestyle.LifestyleRecommendRequest;
import com.onrender.zipai.dto.lifestyle.LifestyleRecommendResponse;
import com.onrender.zipai.service.LifestyleMlBridgeService;
import com.onrender.zipai.service.LifestyleRecommendationService;

@RestController
@RequestMapping("/api/lifestyle")
public class LifestyleRecommendationController {

    private final LifestyleRecommendationService lifestyleRecommendationService;
    private final LifestyleMlBridgeService lifestyleMlBridgeService;

    public LifestyleRecommendationController(
            LifestyleRecommendationService lifestyleRecommendationService,
            LifestyleMlBridgeService lifestyleMlBridgeService) {
        this.lifestyleRecommendationService = lifestyleRecommendationService;
        this.lifestyleMlBridgeService = lifestyleMlBridgeService;
    }

    @GetMapping("/areas")
    public ItemsResponse<LifestyleAreaResponse> areas() {
        return new ItemsResponse<>(lifestyleRecommendationService.getAreas());
    }

    @GetMapping("/areas/{areaId}")
    public ItemResponse<LifestyleAreaResponse> area(@PathVariable Long areaId) {
        return new ItemResponse<>(lifestyleRecommendationService.getArea(areaId));
    }

    @PostMapping("/recommend")
    public LifestyleRecommendResponse recommend(
            @RequestBody LifestyleRecommendRequest request) {
        return lifestyleRecommendationService.recommend(request);
    }

    @PostMapping("/recommend/ml")
    public LifestyleMlRecommendResponse recommendMl(
            @RequestBody LifestyleRecommendRequest request) {
        return lifestyleMlBridgeService.recommend(request);
    }
}
