package com.onrender.zipai.safety.service;

import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class VworldGeocodingClient implements SafetyCoordinateGeocoder {
    private static final Logger log = LoggerFactory.getLogger(VworldGeocodingClient.class);

    private final String apiKey;
    private final RestClient restClient;

    @Autowired
    public VworldGeocodingClient(SafetyProperties properties) {
        this(properties.vworldApiKey(), defaultRestClient());
    }

    public VworldGeocodingClient(String apiKey) {
        this(apiKey, defaultRestClient());
    }

    VworldGeocodingClient(String apiKey, RestClient restClient) {
        this.apiKey = apiKey;
        this.restClient = restClient;
    }

    @Override
    public Optional<Coordinate> geocode(String query) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VWORLD_API_KEY가 없어 주소 좌표 변환을 할 수 없습니다.");
        }
        List<SearchAttempt> attempts = List.of(
            new SearchAttempt("ADDRESS", "ROAD"),
            new SearchAttempt("ADDRESS", "PARCEL"),
            new SearchAttempt("PLACE", null),
            new SearchAttempt("DISTRICT", "L4"),
            new SearchAttempt("DISTRICT", "L3"),
            new SearchAttempt("DISTRICT", "L2")
        );
        for (SearchAttempt attempt : attempts) {
            Optional<Coordinate> coordinate = callVworld(query, attempt);
            if (coordinate.isPresent()) return coordinate;
        }
        return Optional.empty();
    }

    private Optional<Coordinate> callVworld(String query, SearchAttempt attempt) {
        URI requestUri = buildRequestUri(apiKey, query, attempt.type(), attempt.category());
        URI maskedUri = buildRequestUri("***", query, attempt.type(), attempt.category());
        try {
            ResponseEntity<JsonNode> entity = restClient.get().uri(requestUri).retrieve().toEntity(JsonNode.class);
            JsonNode root = entity.getBody();
            JsonNode response = root == null ? null : root.path("response");
            String status = response == null ? "" : response.path("status").asText("");
            if (!"OK".equals(status)) {
                JsonNode error = response == null ? null : response.path("error");
                log.warn(
                    "VWorld geocoding returned httpStatus={} responseStatus={} errorLevel={} errorCode={} errorText={} type={} category={} uri={} body={}",
                    entity.getStatusCode(),
                    status,
                    text(error, "level"),
                    text(error, "code"),
                    text(error, "text"),
                    attempt.type(),
                    attempt.category(),
                    maskedUri.toASCIIString(),
                    snippet(root == null ? "" : root.toString())
                );
                return Optional.empty();
            }
            JsonNode item = response.path("result").path("items").path(0);
            JsonNode point = item.path("point");
            if (point.path("x").isMissingNode() || point.path("y").isMissingNode()) return Optional.empty();
            String address = item.path("address").path("road")
                .asText(item.path("address").path("parcel").asText(item.path("title").asText(query)));
            double latitude = point.path("y").asDouble();
            double longitude = point.path("x").asDouble();
            AdministrativeArea area = lookupAdministrativeArea(latitude, longitude).orElse(null);
            return Optional.of(new Coordinate(
                address,
                latitude,
                longitude,
                area == null ? null : area.sidoName(),
                area == null ? null : area.sigunguName()
            ));
        } catch (RestClientResponseException error) {
            log.warn(
                "VWorld geocoding HTTP error status={} type={} category={} uri={} body={}",
                error.getStatusCode(),
                attempt.type(),
                attempt.category(),
                maskedUri.toASCIIString(),
                snippet(error.getResponseBodyAsString()),
                error
            );
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "주소 검색 공공 API 호출에 실패했습니다.");
        } catch (Exception error) {
            log.warn(
                "VWorld geocoding request failed type={} category={} uri={}",
                attempt.type(),
                attempt.category(),
                maskedUri.toASCIIString(),
                error
            );
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "주소 검색 공공 API 호출에 실패했습니다.");
        }
    }

    private Optional<AdministrativeArea> lookupAdministrativeArea(double latitude, double longitude) {
        URI requestUri = buildAddressUri(apiKey, latitude, longitude);
        URI maskedUri = buildAddressUri("***", latitude, longitude);
        try {
            ResponseEntity<JsonNode> entity = restClient.get().uri(requestUri).retrieve().toEntity(JsonNode.class);
            Optional<AdministrativeArea> area = parseAdministrativeArea(entity.getBody());
            if (area.isEmpty()) {
                log.warn(
                    "VWorld address lookup did not include administrative structure httpStatus={} uri={} body={}",
                    entity.getStatusCode(), maskedUri.toASCIIString(),
                    snippet(entity.getBody() == null ? "" : entity.getBody().toString())
                );
            }
            return area;
        } catch (Exception error) {
            // Coordinates and the display address are still useful if the supplementary
            // structured-address lookup is temporarily unavailable.
            log.warn("VWorld address lookup failed uri={}", maskedUri.toASCIIString(), error);
            return Optional.empty();
        }
    }

    static Optional<AdministrativeArea> parseAdministrativeArea(JsonNode root) {
        JsonNode result = root == null ? null : root.path("response").path("result").path(0);
        JsonNode structure = result == null ? null : result.path("structure");
        String sido = text(structure, "level1").trim();
        String sigungu = text(structure, "level2").trim();
        if (sido.isBlank()) return Optional.empty();
        return Optional.of(new AdministrativeArea(sido, sigungu.isBlank() ? null : sigungu));
    }

    static URI buildRequestUri(String apiKey, String query, String type, String category) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString("https://api.vworld.kr/req/search")
            .queryParam("service", "search")
            .queryParam("request", "search")
            .queryParam("version", "2.0")
            .queryParam("crs", "EPSG:4326")
            .queryParam("size", "1")
            .queryParam("page", "1")
            .queryParam("query", query)
            .queryParam("type", type)
            .queryParam("format", "json")
            .queryParam("errorformat", "json")
            .queryParam("key", apiKey);
        if (category != null) {
            builder.queryParam("category", category);
        }
        return builder.build().encode(StandardCharsets.UTF_8).toUri();
    }

    static URI buildAddressUri(String apiKey, double latitude, double longitude) {
        return UriComponentsBuilder.fromUriString("https://api.vworld.kr/req/address")
            .queryParam("service", "address")
            .queryParam("request", "getAddress")
            .queryParam("version", "2.0")
            .queryParam("crs", "EPSG:4326")
            .queryParam("point", longitude + "," + latitude)
            .queryParam("format", "json")
            .queryParam("type", "both")
            .queryParam("zipcode", "false")
            .queryParam("simple", "false")
            .queryParam("key", apiKey)
            .build().encode(StandardCharsets.UTF_8).toUri();
    }

    private static String snippet(String value) {
        if (value == null || value.isBlank()) return "";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        return node.path(field).asText("");
    }

    private static RestClient defaultRestClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private record SearchAttempt(String type, String category) {
    }

    record AdministrativeArea(String sidoName, String sigunguName) {
    }
}
