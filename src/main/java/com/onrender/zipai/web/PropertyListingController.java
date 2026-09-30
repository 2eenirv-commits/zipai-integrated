package com.onrender.zipai.web;

import com.onrender.zipai.domain.ZipaiUser;
import com.onrender.zipai.service.PropertyListingImageStorageService;
import com.onrender.zipai.service.PropertyListingService;
import com.onrender.zipai.service.ZipaiAuthService;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/properties")
public class PropertyListingController {
    private final PropertyListingService properties;
    private final PropertyListingImageStorageService images;
    private final ZipaiAuthService auth;

    public PropertyListingController(PropertyListingService properties, PropertyListingImageStorageService images, ZipaiAuthService auth) {
        this.properties = properties; this.images = images; this.auth = auth;
    }

    @GetMapping
    public Map<String,Object> list(@RequestParam(required=false) String dealType, @RequestParam(required=false) List<Long> ids,
            @RequestParam(defaultValue="true") boolean includeStudy) {
        var items = properties.find(dealType, ids, includeStudy);
        return Map.of("items", items, "count", items.size());
    }

    @GetMapping("/mine")
    public Map<String,Object> mine(HttpSession session) {
        ZipaiUser user = auth.required(session);
        var items = properties.findMine(user.getId());
        return Map.of("items", items, "count", items.size());
    }

    @PostMapping("/import-check")
    public Map<String,Object> importCheck(@RequestHeader(name="X-Property-Import-Token", required=false) String token) {
        requireImportToken(token);
        return Map.of("success", true);
    }

    @PatchMapping("/{propertyId}/status")
    public Map<String,Object> changeStatus(@PathVariable long propertyId, @RequestBody Map<String,Object> body, HttpSession session) {
        ZipaiUser user = auth.required(session);
        String status = String.valueOf(body.getOrDefault("status", ""));
        return Map.of("success", true, "item", properties.changeMyStatus(user.getId(), propertyId, status));
    }

    @PutMapping(path="/{propertyId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> updateListing(@PathVariable long propertyId, @RequestParam Map<String,String> fields,
            @RequestParam(name="images", required=false) MultipartFile[] files, HttpSession session) {
        ZipaiUser user = auth.required(session);
        return Map.of("success", true, "item", properties.updateMyListing(user.getId(), propertyId, fields, files));
    }

    @DeleteMapping("/{propertyId}")
    public Map<String,Object> deleteListing(@PathVariable long propertyId, HttpSession session) {
        ZipaiUser user = auth.required(session);
        properties.deleteMyListing(user.getId(), propertyId);
        return Map.of("success", true);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Object> createLegacy(@RequestBody Map<String,Object> body, HttpSession session) {
        ZipaiUser user = auth.required(session);
        return Map.of("item", properties.createLegacyRental(user, body));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> createListing(@RequestParam Map<String,String> fields,
            @RequestParam(name="images", required=false) MultipartFile[] files, HttpSession session) {
        ZipaiUser user = auth.required(session);
        return Map.of("item", properties.createListing(user, fields, files));
    }

    @GetMapping("/images/{storedName:.+}")
    public ResponseEntity<Resource> image(@PathVariable String storedName) {
        Resource resource = images.load(storedName);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(images.contentType(storedName))).body(resource);
    }


    @GetMapping("/market-transactions")
    public Map<String,Object> marketTransactions(@RequestParam(required=false) String sido,
            @RequestParam(required=false) String lawdCd,
            @RequestParam(required=false) String dealYmd,
            @RequestParam(required=false) String query,
            @RequestParam(required=false) Long minAmount,
            @RequestParam(required=false) Long maxAmount,
            @RequestParam(required=false) Double minArea,
            @RequestParam(required=false) Double maxArea,
            @RequestParam(required=false) Integer minBuildYear,
            @RequestParam(required=false) String dealingType,
            @RequestParam(required=false) Double minLat, @RequestParam(required=false) Double maxLat,
            @RequestParam(required=false) Double minLng, @RequestParam(required=false) Double maxLng,
            @RequestParam(defaultValue="false") boolean excludeCancelled,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="30") int size) {
        return properties.findMarketTransactions(sido, lawdCd, dealYmd, query, minAmount, maxAmount,
                minArea, maxArea, minBuildYear, dealingType, minLat, maxLat, minLng, maxLng,
                excludeCancelled, page, size);
    }

    @GetMapping("/market-transactions/{transactionId}")
    public Map<String,Object> marketTransaction(@PathVariable long transactionId) {
        return properties.findMarketTransactionDetail(transactionId);
    }

    @PostMapping("/market-import")
    public Map<String,Object> marketImport(@RequestHeader(name="X-Property-Import-Token", required=false) String token,
            @RequestBody Map<String,Object> body) {
        requireImportToken(token);
        String sourceName = String.valueOf(body.getOrDefault("sourceName", "MOLIT_APT_TRADE"));
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> items = body.get("items") instanceof List<?> raw ? (List<Map<String,Object>>) raw : List.of();
        Map<String,Integer> result = properties.importMarketTransactions(sourceName, items);
        Map<String,Object> out = new LinkedHashMap<>(); out.put("success", result.get("errors") == 0); out.putAll(result); return out;
    }

    @PostMapping("/crawl-import")
    public Map<String,Object> crawlImport(@RequestHeader(name="X-Property-Import-Token", required=false) String token,
            @RequestBody Map<String,Object> body) {
        requireImportToken(token);
        String sourceName = String.valueOf(body.getOrDefault("sourceName", "property-crawler"));
        String importRunId = String.valueOf(body.getOrDefault("importRunId", ""));
        boolean studyData = Boolean.parseBoolean(String.valueOf(body.getOrDefault("studyData", false)));
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> items = body.get("items") instanceof List<?> raw ? (List<Map<String,Object>>) raw : List.of();
        Map<String,Integer> result = properties.importCrawled(sourceName, importRunId, studyData, items);
        Map<String,Object> out = new LinkedHashMap<>(); out.put("success", result.get("errors") == 0); out.putAll(result); return out;
    }

    @PostMapping("/crawl-import/finalize")
    public Map<String,Object> finalizeCrawlImport(@RequestHeader(name="X-Property-Import-Token", required=false) String token,
            @RequestBody Map<String,Object> body) {
        requireImportToken(token);
        String sourceName = String.valueOf(body.getOrDefault("sourceName", ""));
        String importRunId = String.valueOf(body.getOrDefault("importRunId", ""));
        int closed = properties.finalizeCrawledSource(sourceName, importRunId);
        return Map.of("success", true, "closed", closed);
    }

    @DeleteMapping("/study-listings")
    public Map<String,Object> deleteStudyListings(@RequestHeader(name="X-Property-Import-Token", required=false) String token) {
        requireImportToken(token);
        int deleted = properties.deleteStudyListings();
        return Map.of("success", true, "deleted", deleted);
    }

    private void requireImportToken(String token) {
        String expected = System.getenv("PROPERTY_IMPORT_TOKEN");
        if (expected == null || expected.isBlank() || token == null || !expected.equals(token)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "매물 Import Token이 올바르지 않습니다.");
        }
    }
}
