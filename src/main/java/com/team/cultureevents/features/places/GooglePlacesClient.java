package com.team.cultureevents.features.places;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.places.domain.dto.PlaceCandidateDTO;

import com.team.cultureevents.features.places.service.PlacesQuotaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Google Places Nearby Search (New) + Place Photos (New) 호출.
 * 좌표 근처의 식당·카페 후보 여러 개를 리스트로 돌려준다(추천/재추천용).
 * SeoulOpenApiClient·OpenAiClient와 동일하게 RestClient + AppProperties + BusinessException 패턴을 따른다.
 *
 * 주의: Google Maps Platform 서비스 약관상 이름·주소·평점·리뷰수·사진은 캐싱(저장 후 재사용)이
 * 금지돼 있다(place_id는 무기한, 좌표는 30일까지만 허용). 그래서 이 클라이언트는 매 요청마다
 * 라이브로 호출하며, DB에 결과를 저장하는 방식으로 바꾸면 안 된다.
 */
@Component
public class GooglePlacesClient {

    static final String ENDPOINT = "https://places.googleapis.com/v1/places:searchNearby";
    private static final String PHOTO_BASE_URL = "https://places.googleapis.com/v1/";
    private static final String PLACE_DETAILS_BASE_URL = "https://places.googleapis.com/v1/places/";

    // 요청한 필드만큼만 과금되므로, 지금 쓰는 필드만 정확히 명시한다.
    // photos·businessStatus·currentOpeningHours는 전부 Pro/Enterprise 등급이라, 이미 rating
    // 때문에 Enterprise인 이 호출에 얹혀도 등급이 안 올라간다(추가 비용 없음).
    private static final String FIELD_MASK = String.join(",",
            "places.id",
            "places.displayName",
            "places.formattedAddress",
            "places.location",
            "places.rating",
            "places.userRatingCount",
            "places.googleMapsUri",
            "places.photos",
            "places.businessStatus",
            "places.currentOpeningHours.openNow",
            "places.regularOpeningHours.weekdayDescriptions"
    );

    private final AppProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final PlacesQuotaService quotaService;

    @Autowired
    public GooglePlacesClient(AppProperties props, RestClient.Builder builder, ObjectMapper objectMapper,
                              PlacesQuotaService quotaService) {
        this(props, builder.build(), objectMapper, quotaService);
    }

    // 테스트에서 MockRestServiceServer로 이미 완성된 RestClient를 바로 넣을 수 있게 하는 생성자.
    // 생성자가 둘이라 스프링이 자동 주입 대상을 못 고르므로, 반드시 위 생성자에 @Autowired를 붙여야 한다.
    GooglePlacesClient(AppProperties props, RestClient restClient, ObjectMapper objectMapper,
                       PlacesQuotaService quotaService) {
        this.props = props;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.quotaService = quotaService;
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
                                                  int radiusMeters, int maxResults, long memberId) {
        PlacesRequestValidator.nearby(latitude, longitude, includedTypes, radiusMeters);
        if (maxResults < 1 || maxResults > 20) throw BusinessException.badRequest("maxResults는 1~20이어야 합니다.");
        String key = props.places().key();
        if (key == null || key.isBlank()) {
            throw unavailable("GOOGLE_PLACES_API_KEY가 설정되지 않았습니다.");
        }

        quotaService.reserve(false, memberId);

        Map<String, Object> body = Map.of(
                "includedTypes", includedTypes,
                "maxResultCount", maxResults,
                "rankPreference", "POPULARITY",
                "languageCode", "ko",
                "regionCode", "KR",
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


    /** 저장된 코스의 placeId를 화면에 표시할 최신 장소 정보로 다시 조회한다. */
    public PlaceCandidateDTO getDetails(String placeId, long memberId) {
        PlacesRequestValidator.placeId(placeId);
        String key = props.places().key();
        if (key == null || key.isBlank()) {
            throw unavailable("GOOGLE_PLACES_API_KEY가 설정되지 않았습니다.");
        }

        quotaService.reserveDetails(memberId);

        String uri = PLACE_DETAILS_BASE_URL + placeId + "?languageCode=ko&regionCode=KR";
        String responseJson;
        try {
            responseJson = restClient.get()
                    .uri(uri)
                    .header("X-Goog-Api-Key", key)
                    .header("X-Goog-FieldMask", FIELD_MASK.replace("places.", ""))
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound e) {
            // 코스에 저장해 둔 장소가 폐업·삭제된 경우. 일시 장애(503)와 구분해 프론트가 안내할 수 있게 한다.
            throw new BusinessException("PLACE_NOT_FOUND", "장소 정보를 찾을 수 없습니다. 폐업했거나 삭제된 장소일 수 있습니다.",
                    HttpStatus.NOT_FOUND);
        } catch (HttpClientErrorException.BadRequest e) {
            throw BusinessException.badRequest("placeId 형식이 올바르지 않습니다.");
        } catch (RestClientException e) {
            throw unavailable("Google Places 상세 조회에 실패했습니다.");
        }

        if (responseJson == null || responseJson.isBlank()) {
            throw unavailable("Google Places 상세 응답이 비어 있습니다.");
        }
        try {
            return parseCandidate(objectMapper.readTree(responseJson));
        } catch (IOException e) {
            throw unavailable("Google Places 상세 응답 파싱에 실패했습니다.");
        }
    }

    /**
     * 검색 결과에 실려온 사진 참조값(photoName)을, 실제로 화면에 띄울 수 있는 이미지 URL로 바꾼다.
     * 후보 전체가 아니라 실제로 보여줄 사진 하나에 대해서만 호출해야 한다(별도 과금 SKU).
     *
     * @param photoName  검색 결과의 PlaceCandidateDTO.photoName() 값 그대로
     * @param maxWidthPx 원하는 이미지 최대 가로 픽셀
     * @return 브라우저가 바로 로드할 수 있는 이미지 URL(구글 CDN, API 키 안 들어있음)
     */
    public String resolvePhotoUri(String photoName, int maxWidthPx, long memberId) {
        PlacesRequestValidator.photo(photoName);
        if (maxWidthPx < 1 || maxWidthPx > 1600) throw BusinessException.badRequest("maxWidthPx는 1~1600이어야 합니다.");
        String key = props.places().key();
        if (key == null || key.isBlank()) {
            throw unavailable("GOOGLE_PLACES_API_KEY가 설정되지 않았습니다.");
        }

        quotaService.reserve(true, memberId);

        // skipHttpRedirect=true로 요청하면 구글이 이미지 대신 photoUri가 담긴 JSON을 돌려준다.
        // 이 photoUri는 API 키가 안 들어있는 순수 CDN 링크라, 프론트에 그대로 넘겨도 안전하다.
        String uri = PHOTO_BASE_URL + photoName + "/media"
                + "?maxWidthPx=" + maxWidthPx
                + "&skipHttpRedirect=true"
                + "&key=" + key;

        String responseJson;
        try {
            responseJson = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw unavailable("Google Places 사진 조회에 실패했습니다.");
        }

        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String photoUri = root.path("photoUri").asText(null);
            if (photoUri == null || photoUri.isBlank()) {
                throw unavailable("Google Places 사진 응답 형식이 올바르지 않습니다.");
            }
            return photoUri;
        } catch (IOException e) {
            throw unavailable("Google Places 사진 응답 파싱에 실패했습니다.");
        }
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
                result.add(parseCandidate(p));
            }
            return result;
        } catch (IOException e) {
            throw unavailable("Google Places 응답 파싱에 실패했습니다.");
        }
    }

    private PlaceCandidateDTO parseCandidate(JsonNode p) {
        String photoName = null;
        List<PlaceCandidateDTO.AuthorAttribution> photoAttributions = new ArrayList<>();
        JsonNode photos = p.get("photos");
        if (photos != null && photos.isArray() && !photos.isEmpty()) {
            JsonNode firstPhoto = photos.get(0);
            photoName = textOrNull(firstPhoto, "name");
            JsonNode attributions = firstPhoto.get("authorAttributions");
            if (attributions != null && attributions.isArray()) {
                for (JsonNode author : attributions) {
                    photoAttributions.add(new PlaceCandidateDTO.AuthorAttribution(
                            textOrNull(author, "displayName"),
                            textOrNull(author, "uri"),
                            textOrNull(author, "photoUri")));
                }
            }
        }
        Boolean openNow = p.path("currentOpeningHours").hasNonNull("openNow")
                ? p.path("currentOpeningHours").get("openNow").asBoolean()
                : null;

        List<String> openingHours = new ArrayList<>();
        JsonNode weekdayDescriptions = p.path("regularOpeningHours").get("weekdayDescriptions");
        if (weekdayDescriptions != null && weekdayDescriptions.isArray()) {
            for (JsonNode description : weekdayDescriptions) {
                if (description.isTextual()) {
                    openingHours.add(description.asText());
                }
            }
        }

        return new PlaceCandidateDTO(
                textOrNull(p, "id"),
                p.path("displayName").path("text").asText(null),
                textOrNull(p, "formattedAddress"),
                p.hasNonNull("rating") ? p.get("rating").asDouble() : null,
                p.hasNonNull("userRatingCount") ? p.get("userRatingCount").asInt() : null,
                p.path("location").hasNonNull("latitude") ? p.path("location").get("latitude").asDouble() : null,
                p.path("location").hasNonNull("longitude") ? p.path("location").get("longitude").asDouble() : null,
                textOrNull(p, "googleMapsUri"),
                photoName,
                photoAttributions,
                textOrNull(p, "businessStatus"),
                openNow,
                openingHours,
                null,
                null,
                null
        );
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static BusinessException unavailable(String message) {
        return new BusinessException("PLACES_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }

}
