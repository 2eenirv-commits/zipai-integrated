package com.onrender.zipai.safety.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.onrender.zipai.safety.dto.RegionalSafetyIndex;
import com.onrender.zipai.safety.dto.RegionalSafetyIndexResult;
import com.onrender.zipai.safety.repository.RegionalSafetyRepository;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegionalSafetyService {
    private static final Pattern PROVINCE_CITY_DISTRICT = Pattern.compile("^([^\\s]+시) ([^\\s]+구)$");
    private static final String SEJONG = "세종특별자치시";
    private static final String JEJU = "제주특별자치도";
    private static final Set<String> SEJONG_LEVEL2_NAMES = Set.of("세종시", SEJONG);
    private static final Set<String> JEJU_ADMINISTRATIVE_CITIES = Set.of("제주시", "서귀포시");
    private final RegionalSafetyRepository repository;

    public RegionalSafetyService(RegionalSafetyRepository repository) {
        this.repository = repository;
    }

    public RegionalSafetyIndexResult lookup(Integer requestedYear, String sidoName, String sigunguName) {
        String sido = requiredName(sidoName, "시도");
        String sigungu = optionalName(sigunguName, "시군구");
        Integer year = requestedYear != null ? requestedYear : repository.latestYear().orElse(null);
        if (year == null) {
            return unavailable(null, sido, sigungu, "적재된 지역안전지수 데이터가 없습니다.");
        }
        Optional<RegionalSafetyIndex> found = find(year, sido, sigungu);
        return found
            .map(value -> new RegionalSafetyIndexResult(
                true, true, year, sido, sigungu, value, "지역안전지수 등급을 조회했습니다."
            ))
            .orElseGet(() -> unavailable(year, sido, sigungu, "해당 지역의 지역안전지수 데이터가 없습니다."));
    }

    private Optional<RegionalSafetyIndex> find(int year, String sido, String sigungu) {
        if (sigungu == null) return repository.findSido(year, sido);
        Optional<RegionalSafetyIndex> exact = repository.findSigunguExact(year, sido, sigungu);
        if (exact.isPresent()) return exact;

        // The official table has no lower-level rows for Sejong or Jeju's two
        // administrative cities, so these structured VWorld names use the sido row.
        if ((SEJONG.equals(sido) && SEJONG_LEVEL2_NAMES.contains(sigungu))
            || (JEJU.equals(sido) && JEJU_ADMINISTRATIVE_CITIES.contains(sigungu))) {
            return repository.findSido(year, sido);
        }

        Matcher hierarchy = PROVINCE_CITY_DISTRICT.matcher(sigungu);
        if (!hierarchy.matches()) return Optional.empty();
        return repository.findSigunguExact(year, sido, hierarchy.group(1));
    }

    private static RegionalSafetyIndexResult unavailable(
        Integer year, String sido, String sigungu, String message
    ) {
        return new RegionalSafetyIndexResult(true, false, year, sido, sigungu, null, message);
    }

    private static String requiredName(String value, String label) {
        String normalized = optionalName(value, label);
        if (normalized == null) {
            throw new ResponseStatusException(BAD_REQUEST, label + " 이름을 입력해 주세요.");
        }
        return normalized;
    }

    private static String optionalName(String value, String label) {
        String normalized = normalize(value);
        if (normalized == null) return null;
        if (normalized.length() > 100) {
            throw new ResponseStatusException(BAD_REQUEST, label + " 이름은 100자 이하로 입력해 주세요.");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }
}
