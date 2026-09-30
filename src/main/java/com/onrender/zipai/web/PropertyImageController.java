package com.onrender.zipai.web;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onrender.zipai.service.PropertyImageStorageService;

@RestController
@RequestMapping("/api/lifestyle/property-images")
public class PropertyImageController {

    private final PropertyImageStorageService storageService;

    public PropertyImageController(PropertyImageStorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/{storedName:.+}")
    public ResponseEntity<Resource> image(@PathVariable String storedName) {
        Resource resource = storageService.load(storedName);
        String contentType = storageService.detectContentType(storedName);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
