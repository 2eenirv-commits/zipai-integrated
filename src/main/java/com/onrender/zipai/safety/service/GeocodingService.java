package com.onrender.zipai.safety.service;

import com.onrender.zipai.safety.dto.SafetyLocation;
import com.onrender.zipai.safety.repository.SafetyRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class GeocodingService {
    private final SafetyRepository safetyRepository;
    private final SafetyCoordinateGeocoder coordinateGeocoder;

    public GeocodingService(SafetyRepository safetyRepository, SafetyCoordinateGeocoder coordinateGeocoder) {
        this.safetyRepository = safetyRepository;
        this.coordinateGeocoder = coordinateGeocoder;
    }

    public List<SafetyLocation> search(String query) {
        String keyword = normalizeQuery(query);
        List<SafetyLocation> saved = safetyRepository.searchLocations(keyword);
        if (!saved.isEmpty()) {
            return enrichAdministrativeArea(keyword, saved);
        }
        return List.of(searchVworld(keyword));
    }

    private List<SafetyLocation> enrichAdministrativeArea(String query, List<SafetyLocation> saved) {
        try {
            return coordinateGeocoder.geocode(query)
                .filter(coordinate -> coordinate.sidoName() != null && !coordinate.sidoName().isBlank())
                .map(coordinate -> saved.stream().map(location -> new SafetyLocation(
                    location.id(), location.name(), location.address(),
                    location.latitude(), location.longitude(), location.sourceName(),
                    location.sourceUpdatedAt(), coordinate.sidoName(), coordinate.sigunguName()
                )).toList())
                .orElse(saved);
        } catch (ResponseStatusException error) {
            // 저장된 중심좌표는 VWorld가 일시적으로 실패해도 안전도 계산에 사용할 수 있다.
            return saved;
        }
    }

    public SafetyLocation first(String query) {
        List<SafetyLocation> locations = search(query);
        if (locations.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "검색 결과가 없습니다.");
        }
        return locations.get(0);
    }

    private SafetyLocation searchVworld(String query) {
        return coordinateGeocoder.geocode(query)
            .map(coordinate -> new SafetyLocation(
                null,
                query,
                coordinate.address(),
                coordinate.latitude(),
                coordinate.longitude(),
                "국토교통부 VWorld Search API",
                LocalDate.now(),
                coordinate.sidoName(),
                coordinate.sigunguName()
            ))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "검색 결과가 없습니다."));
    }

    private static String normalizeQuery(String query) {
        String normalized = query == null ? "" : query.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "검색어를 입력해 주세요.");
        }
        if (normalized.length() > 100) {
            throw new ResponseStatusException(BAD_REQUEST, "검색어는 100자 이하로 입력해 주세요.");
        }
        return normalized;
    }
}
