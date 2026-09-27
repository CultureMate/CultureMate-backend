package com.team.cultureevents.features.places;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.places.domain.dto.PlaceCandidateDTO;
import com.team.cultureevents.features.places.domain.entity.PlacesApiUsageEntity;
import com.team.cultureevents.features.places.repository.PlacesApiUsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Google Places Nearby Search (New) 호출.
 * URL: https://places.googleapis.com/v1/places:searchNearby
 * 좌표 근처의 식당·카페 후보 여러 개를 리스트로 돌려준다(추천/재추천용).
 * SeoulOpenApiClient·OpenAiClient와 동일하게 RestClient + AppProperties + BusinessException 패턴을 따른다.
 *
 * 주의: Google Maps Platform 서비스 약관상 이름·주소·평점·리뷰수는 캐싱(저장 후 재사용)이
 * 금지돼 있다(place_id는 무기한, 좌표는 30일까지만 허용). 그래서 이 클라이언트는 매 요청마다
 * 라이브로 호출하며, DB에 결과를 저장하는 방식으로 바꾸면 안 된다.
 */
@Component
public class GooglePlacesClient {

    static final String ENDPOINT = "https://places.googleapis.com/v1/places:searchNearby";

    // rating·userRatingCount를 요청하면 Enterprise 등급(무료 월 1,000건)으로 과금된다.
    // 하루 한도를 넉넉히 낮게 잡아, 반복 호출이 몰려도 한 달 무료 한도를 못 넘게 막는다.
    // places_api_usage 테이블에 기록하므로 서버 재시작에도 카운트가 유지된다.
    static final int DAILY_CALL_LIMIT = 30;

    // 요청한 필드만큼만 과금되므로, 지금 쓰는 필드만 정확히 명시한다.
    private static final String FIELD_MASK = String.join(",",
            "places.id",
            "places.displayName",
            "places.formattedAddress",
            "places.location",
            "places.rating",
            "places.userRatingCount",
            "places.googleMapsUri"
    );

    private final AppProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final PlacesApiUsageRepository usageRepository;

    private final Object quotaLock = new Object();

    @Autowired
    public GooglePlacesClient(AppProperties props, RestClient.Builder builder, ObjectMapper objectMapper,
                              PlacesApiUsageRepository usageRepository) {
        this(props, builder.build(), objectMapper, usageRepository);
    }

    // 테스트에서 MockRestServiceServer로 이미 완성된 RestClient를 바로 넣을 수 있게 하는 생성자.
    // 생성자가 둘이라 스프링이 자동 주입 대상을 못 고르므로, 반드시 위 생성자에 @Autowired를 붙여야 한다.
    GooglePlacesClient(AppProperties props, RestClient restClient, ObjectMapper objectMapper,
                       PlacesApiUsageRepository usageRepository) {
        this.props = props;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.usageRepository = usageRepository;
    }

    /**
     * 주어진 좌표 근처의 장소 후보를 검색한다.
     *
     * @param latitude      중심 좌표 위도
     * @param longitude     중심 좌표 경도
     * @param includedTypes Google Places가 정의한 타입(예: "restaurant", "cafe")
     * @param radiusMeters  검색 반경(m). Google 제한상 최대 50000
     * @param maxResults    최대 후보 수(1~20)
     * @return 후보 리스트. 검색 자체는 성공했지만 근처에 아무것도 없으면 빈 리스트(에러 아님).
     */
    public List<PlaceCandidateDTO> searchNearby(double latitude, double longitude,
                                                 List<String> includedTypes,
                                                 int radiusMeters, int maxResults) {
        String key = props.places().key();
        if (key == null || key.isBlank()) {
            throw unavailable("GOOGLE_PLACES_API_KEY가 설정되지 않았습니다.");
        }

        checkAndIncrementDailyQuota();

        Map<String, Object> body = Map.of(
                "includedTypes", includedTypes,
                "maxResultCount", maxResults,
                "rankPreference", "POPULARITY",
                "locationRestriction", Map.of(
                        "circle", Map.of(
                                "center", Map.of("latitude", latitude, "longitude", longitude),
                                "radius", (double) radiusMeters
                        )
                )
        );

        String responseJson;
        try {
            responseJson = restClient.post()
                    .uri(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", key)
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw unavailable("Google Places 호출에 실패했습니다.");
        }

        return parseCandidates(responseJson);
    }

    private List<PlaceCandidateDTO> parseCandidates(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            // 근처에 결과가 없을 때도 Google이 빈 본문을 줄 수 있어, 에러가 아니라 빈 리스트로 처리한다.
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode places = root.get("places");
            if (places == null || !places.isArray()) {
                return List.of();
            }
            List<PlaceCandidateDTO> result = new ArrayList<>();
            for (JsonNode p : places) {
                result.add(new PlaceCandidateDTO(
                        textOrNull(p, "id"),
                        p.path("displayName").path("text").asText(null),
                        textOrNull(p, "formattedAddress"),
                        p.hasNonNull("rating") ? p.get("rating").asDouble() : null,
                        p.hasNonNull("userRatingCount") ? p.get("userRatingCount").asInt() : null,
                        p.path("location").hasNonNull("latitude") ? p.path("location").get("latitude").asDouble() : null,
                        p.path("location").hasNonNull("longitude") ? p.path("location").get("longitude").asDouble() : null,
                        textOrNull(p, "googleMapsUri")
                ));
            }
            return result;
        } catch (IOException e) {
            throw unavailable("Google Places 응답 파싱에 실패했습니다.");
        }
    }

    /** 하루가 바뀌면 새 행을 만들고, 오늘 호출 수가 한도를 넘으면 실제 호출 전에 막는다. */
    @Transactional
    void checkAndIncrementDailyQuota() {
        synchronized (quotaLock) {
            LocalDate today = LocalDate.now();
            PlacesApiUsageEntity usage = usageRepository.findById(today)
                    .orElseGet(() -> new PlacesApiUsageEntity(today, 0));
            if (usage.getCallCount() >= DAILY_CALL_LIMIT) {
                throw quotaExceeded("오늘 Google Places 호출 한도(" + DAILY_CALL_LIMIT + "건)를 다 썼습니다.");
            }
            usage.incrementCallCount();
            usageRepository.save(usage);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static BusinessException unavailable(String message) {
        return new BusinessException("PLACES_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static BusinessException quotaExceeded(String message) {
        return new BusinessException("PLACES_QUOTA_EXCEEDED", message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
