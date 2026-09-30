package com.onrender.zipai.web;

import com.onrender.zipai.domain.ZipaiUser;
import com.onrender.zipai.service.PropertyListingService;
import com.onrender.zipai.service.ZipaiAuthService;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favorites")
public class PropertyFavoriteController {
    private final PropertyListingService properties;
    private final ZipaiAuthService auth;
    public PropertyFavoriteController(PropertyListingService properties, ZipaiAuthService auth) { this.properties=properties; this.auth=auth; }

    @GetMapping
    public Map<String,Object> get(HttpSession session) {
        ZipaiUser user = auth.required(session);
        List<Long> ids = properties.favoriteIds(user.getId());
        return Map.of("ids", ids, "items", properties.find(null, ids, true));
    }

    @PutMapping
    public Map<String,Object> put(@RequestBody Map<String,Object> body, HttpSession session) {
        ZipaiUser user = auth.required(session);
        List<Long> ids = new ArrayList<>();
        if (body.get("ids") instanceof List<?> raw) for (Object item : raw) if (item != null) ids.add(Long.valueOf(String.valueOf(item)));
        properties.replaceFavorites(user.getId(), ids);
        return Map.of("success", true, "ids", properties.favoriteIds(user.getId()));
    }
}
