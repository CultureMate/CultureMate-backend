package com.team.cultureevents.features.places.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.events.domain.dto.EventDetailResponseDTO;
import com.team.cultureevents.features.events.service.EventService;
import com.team.cultureevents.features.places.GooglePlacesClient;
import com.team.cultureevents.features.places.PlacesRequestValidator;
import com.team.cultureevents.features.places.domain.dto.PlaceCandidateDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 좌표 근처 식당·카페 추천. 추천 결과 자체는 서버에 저장하지 않는다.
 * "다시 추천"은 이 API를 다시 부르는 게 아니라, 한 번에 받은 리스트를 프론트가 순서대로 보여주는 방식을 전제로 한다.
 *
 * Google Places Nearby Search는 "평점순" 정렬 옵션이 없어(POPULARITY/DISTANCE만 지원),
 * 항상 최대치(20개)를 받아온 뒤 이 클래스에서 직접 점수를 매겨 정렬한다.
 */
@Service
public class PlacesService {

    static final List<String> DEFAULT_TYPES = List.of("restaurant", "cafe");
    static final int DEFAULT_RADIUS_METERS = 500;
    static final int DEFAULT_MAX_RESULTS = 5;
    static final int RESULT_LIMIT = 20; // Places Nearby Search(New) 자체 상한, 페이지네이션 없음
    static final int SEARCH_POOL_SIZE = RESULT_LIMIT; // 정렬 재료를 최대한 확보하기 위해 항상 상한까지 요청
    static final int MIN_REVIEW_COUNT = 5; // 리뷰가 너무 적으면 아예 후보에서 제외

    // 영구 폐업/장기 휴업 상태는 추천 자체가 무의미해서 항상 제외한다. 다만 구글 데이터도
    // 100% 실시간은 아니라서, 이 필터가 "가보니 폐업" 위험을 완전히 없애주진 못한다.
    private static final Set<String> EXCLUDED_BUSINESS_STATUS = Set.of("CLOSED_PERMANENTLY", "CLOSED_TEMPORARILY");

    // 두 행사가 이 거리 이하로 가까울 때만 중간점 한 곳을 검색한다. 더 멀면 중간점이 어느 행사에서도
    // 걸어가기 애매한 곳이 되기 쉬워, 각 행사 근처를 따로 검색한다.
    static final double MIDPOINT_SEARCH_MAX_DISTANCE_METERS = 1_500;
    static final int NEAR_EVENT_RADIUS_METERS = 750;
    static final int MIN_BETWEEN_RADIUS_METERS = 100;

    // 베이지안 가중평균(IMDB 방식)의 m값: 리뷰가 이 값보다 훨씬 많아야 자기 평점을 온전히 인정받는다.
    // 값을 올리면 "리뷰 많은 곳"을 더 우대하고, 낮추면 "평점 자체"를 더 신뢰한다. 정답은 없고 팀이 튜닝하는 값.
    static final double BAYESIAN_MIN_VOTES = 10.0;
    static final double MIN_ALLOWED_DETOUR_METERS = 300;
    static final double MAX_ALLOWED_DETOUR_METERS = 2000;
    static final double MAX_DETOUR_PENALTY = 0.5;

    private final GooglePlacesClient placesClient;
    private final EventService eventService;

    public PlacesService(GooglePlacesClient placesClient, EventService eventService) {
        this.placesClient = placesClient;
        this.eventService = eventService;
    }

    /** 좌표 하나를 직접 주고 검색하는 범용 방식(행사 상세 페이지 보강 등에 사용). */
    public List<PlaceCandidateDTO> recommendNearby(Double latitude, Double longitude,
                                                    List<String> types, Integer radiusMeters, Integer maxResults,
                                                    long memberId) {
        if (latitude == null || longitude == null) {
            throw BusinessException.badRequest("latitude, longitude가 필요합니다.");
        }
        validateKoreaBounds(latitude, longitude);

        List<String> resolvedTypes = (types == null || types.isEmpty()) ? DEFAULT_TYPES : types;
        int resolvedRadius = radiusMeters == null ? DEFAULT_RADIUS_METERS : radiusMeters;
        PlacesRequestValidator.nearby(latitude, longitude, resolvedTypes, resolvedRadius);
        int resolvedMax = maxResults == null
                ? DEFAULT_MAX_RESULTS
                : Math.min(Math.max(maxResults, 1), RESULT_LIMIT);

        List<PlaceCandidateDTO> pool = placesClient.searchNearby(
                latitude, longitude, resolvedTypes, resolvedRadius, SEARCH_POOL_SIZE, memberId);

        return filterAndSort(pool).stream().limit(resolvedMax).toList();
    }

    /**
     * 코스에서 연속된 두 행사(eventId1 -> eventId2)를 이어 가기 좋은 카페/음식점을 찾는다.
     * 두 행사가 1.5km 이하면 중간점을 한 번 검색하고, 더 멀면 각 행사 근처(반경 750m)를 한 번씩 검색한다.
     * 후보는 베이지안 평점과 우회거리로 점수를 매긴 뒤, 더 가까운 행사 쪽(nearEventId)으로 나눠
     * 양쪽이 번갈아 나오도록 최대 20개를 돌려준다.
     * "5개씩 페이지로 넘겨보기"는 프론트가 이 배열 안에서 나눠서 처리한다(추가 호출 없음).
     *
     * @param type "cafe" 또는 "restaurant" (한 번에 하나의 카테고리만)
     */
    public List<PlaceCandidateDTO> recommendBetweenEvents(String eventId1, String eventId2, String type,
                                                           long memberId) {
        if (eventId1 == null || eventId1.isBlank() || eventId2 == null || eventId2.isBlank()) {
            throw BusinessException.badRequest("eventId1, eventId2가 필요합니다.");
        }
        if (type == null || !DEFAULT_TYPES.contains(type)) {
            throw BusinessException.badRequest("type은 cafe 또는 restaurant이어야 합니다.");
        }

        EventDetailResponseDTO event1 = eventService.getDetail(eventId1);
        EventDetailResponseDTO event2 = eventService.getDetail(eventId2);

        if (event1.latitude() == null || event1.longitude() == null
                || event2.latitude() == null || event2.longitude() == null) {
            throw BusinessException.badRequest("좌표 정보가 없는 행사가 포함되어 있습니다.");
        }

        validateKoreaBounds(event1.latitude(), event1.longitude());
        validateKoreaBounds(event2.latitude(), event2.longitude());
        double distanceMeters = haversineMeters(event1.latitude(), event1.longitude(),
                event2.latitude(), event2.longitude());

        List<PlaceCandidateDTO> pool = new ArrayList<>();
        if (distanceMeters <= MIDPOINT_SEARCH_MAX_DISTANCE_METERS) {
            double centerLat = (event1.latitude() + event2.latitude()) / 2;
            double centerLng = (event1.longitude() + event2.longitude()) / 2;
            int radius = (int) Math.round(Math.max(distanceMeters / 2, MIN_BETWEEN_RADIUS_METERS));
            pool.addAll(placesClient.searchNearby(centerLat, centerLng, List.of(type), radius, RESULT_LIMIT, memberId));
        } else {
            pool.addAll(placesClient.searchNearby(event1.latitude(), event1.longitude(), List.of(type),
                    NEAR_EVENT_RADIUS_METERS, RESULT_LIMIT, memberId));
            pool.addAll(placesClient.searchNearby(event2.latitude(), event2.longitude(), List.of(type),
                    NEAR_EVENT_RADIUS_METERS, RESULT_LIMIT, memberId));
        }
        pool = distinctByPlaceId(pool);

        // Geographic estimates, not walking/driving route lengths.
        double allowedDetour = Math.min(MAX_ALLOWED_DETOUR_METERS,
                Math.max(MIN_ALLOWED_DETOUR_METERS, distanceMeters * 0.5));
        List<PlaceCandidateDTO> eligible = filterAndSort(pool).stream()
                .filter(p -> p.latitude() != null && p.longitude() != null
                        && Double.isFinite(p.latitude()) && Double.isFinite(p.longitude())
                        && p.latitude() >= 33 && p.latitude() <= 39
                        && p.longitude() >= 124 && p.longitude() <= 132)
                .map(p -> p.ranked(Math.max(0,
                        haversineMeters(event1.latitude(), event1.longitude(), p.latitude(), p.longitude())
                        + haversineMeters(p.latitude(), p.longitude(), event2.latitude(), event2.longitude())
                        - distanceMeters), 0))
                .filter(p -> p.detourMeters() <= allowedDetour)
                .toList();
        double average = eligible.stream().mapToDouble(PlaceCandidateDTO::rating).average().orElse(0);
        List<PlaceCandidateDTO> ranked = eligible.stream()
                .map(p -> p.ranked(p.detourMeters(), bayesianScore(p.rating(), p.userRatingCount(), average)
                        - MAX_DETOUR_PENALTY * p.detourMeters() / allowedDetour))
                .sorted(Comparator.comparingDouble(PlaceCandidateDTO::recommendationScore).reversed()
                        .thenComparingDouble(PlaceCandidateDTO::detourMeters)
                        .thenComparing(PlaceCandidateDTO::placeId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(p -> p.near(haversineMeters(event1.latitude(), event1.longitude(), p.latitude(), p.longitude())
                        <= haversineMeters(p.latitude(), p.longitude(), event2.latitude(), event2.longitude())
                        ? eventId1 : eventId2))
                .toList();
        return alternateByNearEvent(ranked, eventId1, RESULT_LIMIT);
    }

    /** 두 번 검색하면 같은 장소가 양쪽에 모두 나올 수 있어, 먼저 나온 것 하나만 남긴다. */
    private static List<PlaceCandidateDTO> distinctByPlaceId(List<PlaceCandidateDTO> pool) {
        Set<String> seen = new HashSet<>();
        return pool.stream()
                .filter(p -> p.placeId() == null || seen.add(p.placeId()))
                .toList();
    }

    /**
     * 점수순 목록을 가까운 행사별로 나눈 뒤, 점수가 더 높은 쪽부터 한 개씩 번갈아 담는다.
     * 한쪽이 먼저 떨어지면 나머지는 남은 쪽 순서대로 채운다.
     */
    static List<PlaceCandidateDTO> alternateByNearEvent(List<PlaceCandidateDTO> ranked, String firstEventId, int limit) {
        List<PlaceCandidateDTO> first = ranked.stream().filter(p -> firstEventId.equals(p.nearEventId())).toList();
        List<PlaceCandidateDTO> second = ranked.stream().filter(p -> !firstEventId.equals(p.nearEventId())).toList();
        List<PlaceCandidateDTO> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        boolean takeFirst = !first.isEmpty()
                && (second.isEmpty() || first.get(0).recommendationScore() >= second.get(0).recommendationScore());
        while (result.size() < limit && (i < first.size() || j < second.size())) {
            if ((takeFirst && i < first.size()) || j >= second.size()) {
                result.add(first.get(i++));
            } else {
                result.add(second.get(j++));
            }
            takeFirst = !takeFirst;
        }
        return result;
    }

    /** 저장된 코스의 placeId를 최신 Google 장소 정보로 다시 조회한다. */
    public PlaceCandidateDTO getDetails(String placeId, long memberId) {
        PlacesRequestValidator.placeId(placeId);
        return placesClient.getDetails(placeId, memberId);
    }

    /** 특정 사진 참조값을, 실제 화면에 띄울 수 있는 이미지 URL로 바꿔서 돌려준다. */
    public String resolvePhotoUri(String photoName, Integer maxWidthPx, long memberId) {
        PlacesRequestValidator.photo(photoName);
        int resolvedWidth = maxWidthPx == null ? 400 : Math.min(Math.max(maxWidthPx, 1), 1600);
        return placesClient.resolvePhotoUri(photoName, resolvedWidth, memberId);
    }

    /** 평점·리뷰수 없는 곳과 폐업/휴업 상태를 걸러내고, 베이지안 점수 내림차순으로 정렬한다. */
    private List<PlaceCandidateDTO> filterAndSort(List<PlaceCandidateDTO> pool) {
        List<PlaceCandidateDTO> filtered = pool.stream()
                .filter(p -> p.rating() != null && p.userRatingCount() != null && p.userRatingCount() >= MIN_REVIEW_COUNT)
                .filter(p -> p.businessStatus() == null || !EXCLUDED_BUSINESS_STATUS.contains(p.businessStatus()))
                .toList();

        double poolAverageRating = filtered.stream()
                .mapToDouble(PlaceCandidateDTO::rating)
                .average()
                .orElse(0.0);

        return filtered.stream()
                .sorted(Comparator.comparingDouble(
                        (PlaceCandidateDTO p) -> bayesianScore(p.rating(), p.userRatingCount(), poolAverageRating)
                ).reversed())
                .toList();
    }

    private static void validateKoreaBounds(double latitude, double longitude) {
        PlacesRequestValidator.coordinates(latitude, longitude);
    }

    /** 두 좌표 사이의 지표면상 직선거리(m)를 하버사인 공식으로 계산한다. */
    static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusMeters = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusMeters * c;
    }

    /**
     * 평점 하나만 보면 "리뷰 5개짜리 만점"이 "리뷰 1000개짜리 4.3점"보다 위로 갈 수 있어 신뢰도가 낮다.
     * IMDB Top 250에서 쓰는 것과 같은 베이지안 가중평균으로 보정한다.
     *
     * score = (v / (v+m)) * R + (m / (v+m)) * C
     *   R = 이 장소의 평점, v = 이 장소의 리뷰수
     *   m = BAYESIAN_MIN_VOTES(신뢰 기준 리뷰수), C = 후보군 전체의 평균 평점
     */
    static double bayesianScore(double rating, int reviewCount, double poolAverageRating) {
        double v = reviewCount;
        double m = BAYESIAN_MIN_VOTES;
        return (v / (v + m)) * rating + (m / (v + m)) * poolAverageRating;
    }
}
