package com.onrender.zipai.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.onrender.zipai.dto.lifestyle.ItemsResponse;
import com.onrender.zipai.dto.lifestyle.LifestylePropertyResponse;
import com.onrender.zipai.service.LifestylePropertyService;

@RestController
@RequestMapping("/api/lifestyle/properties")
public class LifestylePropertyController {
    private final LifestylePropertyService service;

    public LifestylePropertyController(LifestylePropertyService service) {
        this.service = service;
    }

    @GetMapping
    public ItemsResponse<LifestylePropertyResponse> properties(
            @RequestParam(required=false) String sido,
            @RequestParam String sigungu) {
        return new ItemsResponse<>(service.getProperties(sido, sigungu));
    }
}
