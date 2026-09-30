package com.onrender.zipai.safety.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.onrender.zipai.safety.dto.CrimeStatisticsResult;
import com.onrender.zipai.safety.dto.CrimeTypeStatistic;
import com.onrender.zipai.safety.dto.PoliceStationCrimeStatistic;
import com.onrender.zipai.safety.dto.WomenSafetyGuardHouse;
import com.onrender.zipai.safety.dto.WomenSafetyGuardHouseResult;
import com.onrender.zipai.safety.dto.WomenSafetyFacility;
import com.onrender.zipai.safety.dto.WomenSafetyFacilityResult;
import com.onrender.zipai.safety.repository.SeoulSafetyInformationRepository;
import com.onrender.zipai.safety.repository.SeoulSafetyInformationRepository.CrimeRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SeoulSafetyInformationService {
    private static final String SEOUL = "서울특별시";
    private static final String GYEONGGI = "경기도";
    private static final String SEOUL_AGENCY = "서울특별시경찰청";
    private static final String SOUTH_AGENCY = "경기남부경찰청";
    private static final String NORTH_AGENCY = "경기북부경찰청";
    private static final String STATION_COVERAGE = "POLICE_STATION_STATISTICS";
    private static final String AGENCY_COVERAGE = "POLICE_AGENCY_STATISTICS";
    private static final String CRIME_NOTICE = "경찰서 관할 기준 통계 또는 경찰청 관내 전체 통계이며, 검색 지점 반경 내 실제 범죄 발생 건수를 의미하지 않습니다.";
    private static final String GUARD_NOTICE = "2019-11-04 기준 공식 자료로 현재 운영 여부는 실제 현황과 다를 수 있습니다.";
    private static final List<String> CRIME_TYPES = List.of("살인", "강도", "강간·추행", "절도", "폭력");
    private static final Set<String> GYEONGGI_NORTH = Set.of(
        "고양시", "의정부시", "남양주시", "파주시", "구리시", "양주시", "동두천시", "포천시", "가평군", "연천군"
    );
    private static final Set<String> GYEONGGI_SOUTH = Set.of(
        "수원시", "안양시", "군포시", "성남시", "부천시", "광명시", "안산시", "시흥시", "평택시", "오산시",
        "화성시", "용인시", "광주시", "김포시", "하남시", "과천시", "의왕시", "이천시", "안성시", "여주시", "양평군"
    );
    private static final Set<String> DISTRICTED_CITIES = Set.of("수원시", "성남시", "안산시", "고양시", "용인시", "부천시");

    private final SeoulSafetyInformationRepository repository;
    private final SafetyProperties properties;

    public SeoulSafetyInformationService(SeoulSafetyInformationRepository repository, SafetyProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public CrimeStatisticsResult crimeStatistics(String sido, String sigungu, Integer year) {
        String sidoName = normalizeSido(sido);
        String sigunguName = sigungu == null || sigungu.isBlank() ? null : normalizeSigungu(sidoName, sigungu);
        int targetYear = year == null ? defaultCrimeYear(sidoName, sigunguName) : year;
        if (targetYear < 1900 || targetYear > LocalDate.now().getYear()) {
            throw new ResponseStatusException(BAD_REQUEST, "조회 연도가 올바르지 않습니다.");
        }
        if (SEOUL.equals(sidoName)) {
            return crimeResult(targetYear, SEOUL, sigunguName, SEOUL_AGENCY, STATION_COVERAGE);
        }
        if (!GYEONGGI.equals(sidoName) || sigunguName == null) {
            return unavailableCrime(targetYear, sidoName, sigunguName, null, STATION_COVERAGE,
                "해당 지역의 공식 5대범죄 통계가 제공되지 않습니다.");
        }
        if (GYEONGGI_SOUTH.contains(sigunguName)) {
            return crimeResult(targetYear, GYEONGGI, sigunguName, SOUTH_AGENCY, STATION_COVERAGE);
        }
        if (GYEONGGI_NORTH.contains(sigunguName)) {
            return crimeResult(targetYear, GYEONGGI, sigunguName, NORTH_AGENCY, AGENCY_COVERAGE);
        }
        return unavailableCrime(targetYear, sidoName, sigunguName, null, STATION_COVERAGE,
            "해당 지역의 공식 5대범죄 통계가 제공되지 않습니다.");
    }

    private CrimeStatisticsResult crimeResult(
        int targetYear, String sidoName, String sigunguName, String policeAgency, String coverageType
    ) {
        String querySigungu = AGENCY_COVERAGE.equals(coverageType) ? null : sigunguName;
        List<CrimeRow> rows = repository.findCrimeStatistics(
            targetYear, sidoName, querySigungu, policeAgency, coverageType
        );
        if (rows.isEmpty()) {
            return unavailableCrime(targetYear, sidoName, sigunguName, policeAgency, coverageType,
                "해당 연도·지역의 5대범죄 통계가 없습니다.");
        }

        Map<String, List<CrimeTypeStatistic>> byStation = new LinkedHashMap<>();
        Map<String, String> stationDistricts = new LinkedHashMap<>();
        Map<String, int[]> totals = new LinkedHashMap<>();
        CRIME_TYPES.forEach(type -> totals.put(type, new int[2]));
        for (CrimeRow row : rows) {
            byStation.computeIfAbsent(row.policeStationName(), ignored -> new ArrayList<>()).add(row.statistic());
            stationDistricts.put(row.policeStationName(), row.sigunguName());
            int[] total = totals.get(row.statistic().crimeType());
            if (total != null) {
                total[0] += row.statistic().occurrenceCount();
                total[1] += row.statistic().arrestCount();
            }
        }
        List<PoliceStationCrimeStatistic> stations = byStation.entrySet().stream()
            .map(entry -> new PoliceStationCrimeStatistic(entry.getKey(), stationDistricts.get(entry.getKey()), entry.getValue()))
            .toList();
        List<CrimeTypeStatistic> totalList = totals.entrySet().stream()
            .map(entry -> new CrimeTypeStatistic(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
            .toList();
        CrimeRow first = rows.get(0);
        return new CrimeStatisticsResult(
            true, true, first.coverageType(), first.policeAgency(), first.provisional(), first.coverageNote(),
            targetYear, sidoName, sigunguName,
            stations, totalList, first.sourceName(), first.sourceUpdatedAt(), "조회되었습니다.", CRIME_NOTICE
        );
    }

    public WomenSafetyGuardHouseResult womenSafeHouses(Double lat, Double lng, Integer radius, String sido) {
        validateCoordinate(lat, lng);
        int radiusMeters = radius == null ? properties.defaultRadiusMeters() : radius;
        if (radiusMeters < 100 || radiusMeters > properties.maxRadiusMeters()) {
            throw new ResponseStatusException(BAD_REQUEST,
                "분석 반경은 100m 이상 " + properties.maxRadiusMeters() + "m 이하로 입력해 주세요.");
        }
        String sidoName = sido == null || sido.isBlank() ? null : normalizeSido(sido);
        boolean seoulCoordinate = lat >= 37.413294 && lat <= 37.715133 && lng >= 126.734086 && lng <= 127.269311;
        if ((sidoName != null && !SEOUL.equals(sidoName)) || (sidoName == null && !seoulCoordinate)) {
            return new WomenSafetyGuardHouseResult(
                true, false, SEOUL, "SEOUL_SIDO_SIGUNGU", radiusMeters, 0, List.of(), null, null,
                "해당 지역의 공식 여성안전 위치 데이터가 제공되지 않습니다.", GUARD_NOTICE
            );
        }
        List<WomenSafetyGuardHouse> data = repository.findGuardHouses(lat, lng, radiusMeters);
        LocalDate updatedAt = data.stream().map(WomenSafetyGuardHouse::sourceUpdatedAt)
            .filter(java.util.Objects::nonNull).max(LocalDate::compareTo)
            .or(() -> repository.latestGuardHouseDate()).orElse(null);
        boolean available = updatedAt != null;
        return new WomenSafetyGuardHouseResult(
            true, available, SEOUL, "SEOUL_SIDO_SIGUNGU", radiusMeters, data.size(), data,
            available ? "서울특별시_여성안심지킴이집 정보" : null, updatedAt,
            available ? (data.isEmpty() ? "검색 반경 내 데이터가 없습니다." : "조회되었습니다.")
                : "좌표가 검증된 여성안심지킴이집 데이터가 아직 적재되지 않았습니다.",
            GUARD_NOTICE
        );
    }

    public WomenSafetyFacilityResult womenSafetyFacilities(
        Double lat, Double lng, Integer radius, String sido, String sigungu
    ) {
        validateCoordinate(lat, lng);
        int radiusMeters = radius == null ? properties.defaultRadiusMeters() : radius;
        if (radiusMeters < 100 || radiusMeters > properties.maxRadiusMeters()) {
            throw new ResponseStatusException(BAD_REQUEST,
                "분석 반경은 100m 이상 " + properties.maxRadiusMeters() + "m 이하로 입력해 주세요.");
        }
        String sidoName = sido == null || sido.isBlank() ? null : normalizeSido(sido);
        String sigunguName = sigungu == null || sigungu.isBlank() ? null : normalizeSigungu(sidoName, sigungu);
        if (sidoName != null && !Set.of(SEOUL, GYEONGGI).contains(sidoName)) {
            return unavailableWomenFacilities(radiusMeters, sidoName, List.of());
        }
        String lookupSido = sidoName == null
            ? (lat >= 37.413294 && lat <= 37.715133 && lng >= 126.734086 && lng <= 127.269311 ? SEOUL : GYEONGGI)
            : sidoName;
        List<String> supportedRegions = repository.supportedWomenSafetyRegions(lookupSido);
        if (supportedRegions.isEmpty() || (sigunguName != null && !supportedRegions.contains(sigunguName))) {
            return unavailableWomenFacilities(radiusMeters, lookupSido, supportedRegions);
        }
        List<WomenSafetyFacility> data = repository.findWomenSafetyFacilities(lat, lng, radiusMeters, lookupSido);
        String coverage = sigunguName == null ? lookupSido : lookupSido + " " + sigunguName;
        return new WomenSafetyFacilityResult(
            true, true, coverage, supportedRegions, "VERIFIED_ONLY", radiusMeters, data.size(), data,
            data.isEmpty() ? "검색 반경 내 여성안전시설이 없습니다." : "조회되었습니다.",
            "공식 좌표 또는 행정구역이 검증된 좌표만 표시하며 기존 안전 인프라 점수에는 합산되지 않습니다."
        );
    }

    private static WomenSafetyFacilityResult unavailableWomenFacilities(
        int radiusMeters, String coverage, List<String> supportedRegions
    ) {
        return new WomenSafetyFacilityResult(
            true, false, coverage, supportedRegions, "VERIFIED_ONLY", radiusMeters, 0, List.of(),
            "해당 지역 공식 여성안전시설 데이터 미제공",
            "공식 좌표 또는 행정구역이 검증된 좌표만 표시합니다."
        );
    }

    private static CrimeStatisticsResult unavailableCrime(
        int year, String sido, String sigungu, String policeAgency, String coverageType, String message
    ) {
        return new CrimeStatisticsResult(
            true, false, coverageType, policeAgency, false, null, year, sido, sigungu,
            List.of(), List.of(), null, null, message, CRIME_NOTICE
        );
    }

    private static int defaultCrimeYear(String sidoName, String sigunguName) {
        if (GYEONGGI.equals(sidoName) && sigunguName != null && GYEONGGI_SOUTH.contains(sigunguName)) return 2022;
        return 2024;
    }

    private static String normalizeSigungu(String sidoName, String value) {
        String name = normalizeName(value, "시군구");
        if (!GYEONGGI.equals(sidoName)) return name;
        int separator = name.indexOf(' ');
        if (separator <= 0) return name;
        String parent = name.substring(0, separator);
        String child = name.substring(separator + 1);
        if (DISTRICTED_CITIES.contains(parent) && child.endsWith("구") && !child.contains(" ")) return parent;
        return name;
    }

    private static String normalizeSido(String value) {
        String name = normalizeName(value, "시도");
        return switch (name) {
            case "서울", "서울시" -> SEOUL;
            case "경기" -> GYEONGGI;
            default -> name;
        };
    }

    private static String normalizeName(String value, String label) {
        String name = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (name.isBlank()) throw new ResponseStatusException(BAD_REQUEST, label + " 이름을 입력해 주세요.");
        if (name.length() > 100) throw new ResponseStatusException(BAD_REQUEST, label + " 이름이 너무 깁니다.");
        return name;
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
