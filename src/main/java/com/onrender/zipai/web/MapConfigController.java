package com.onrender.zipai.web;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MapConfigController {

    private final String kakaoMapJavaScriptKey;

    public MapConfigController(
        @Value("${zipai.maps.kakao-javascript-key:}") String kakaoMapJavaScriptKey
    ) {
        this.kakaoMapJavaScriptKey = kakaoMapJavaScriptKey;
    }

    @GetMapping("/api/config/maps")
    public Map<String, String> mapConfig() {
        return Map.of("kakaoMapJavaScriptKey", kakaoMapJavaScriptKey);
    }
}
