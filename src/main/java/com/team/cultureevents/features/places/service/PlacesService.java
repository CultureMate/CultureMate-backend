package com.team.cultureevents.features.places.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.places.GooglePlacesClient;
import com.team.cultureevents.features.places.domain.dto.PlaceCandidateDTO;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 좌표 근처 식당·카페 추천. 서버는 아무것도 저장하지 않는다("코스"는 프론트가 로컬에서 관리).
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

    // 베이지안 가중평균(IMDB 방식)의 m값: 리뷰가 이 값보다 훨씬 많아야 자기 평점을 온전히 인정받는다.
    // 값을 올리면 "리뷰 많은 곳"을 더 우대하고, 낮추면 "평점 자체"를 더 신뢰한다. 정답은 없고 팀이 튜닝하는 값.
    static final double BAYESIAN_MIN_VOTES = 10.0;

    private final GooglePlacesClient placesClient;

    public PlacesService(GooglePlacesClient placesClient) {
        this.placesClient = placesClient;
    }

    public List<PlaceCandidateDTO> recommendNearby(Double latitude, Double longitude,
                                                    List<String> types, Integer radiusMeters, Integer maxResults) {
        if (latitude == null || longitude == null) {
            throw BusinessException.badRequest("latitude, longitude가 필요합니다.");
        }
        if (latitude < 33 || latitude > 39 || longitude < 124 || longitude > 132) {
            throw BusinessException.badRequest("좌표가 서울 범위를 벗어났습니다.");
        }

        List<String> resolvedTypes = (types == null || types.isEmpty()) ? DEFAULT_TYPES : types;
        int resolvedRadius = radiusMeters == null ? DEFAULT_RADIUS_METERS : radiusMeters;
        int resolvedMax = maxResults == null
                ? DEFAULT_MAX_RESULTS
                : Math.min(Math.max(maxResults, 1), RESULT_LIMIT);

        List<PlaceCandidateDTO> pool = placesClient.searchNearby(
                latitude, longitude, resolvedTypes, resolvedRadius, SEARCH_POOL_SIZE);

        List<PlaceCandidateDTO> filtered = pool.stream()
                .filter(p -> p.rating() != null && p.userRatingCount() != null && p.userRatingCount() >= MIN_REVIEW_COUNT)
                .toList();

        double poolAverageRating = filtered.stream()
                .mapToDouble(PlaceCandidateDTO::rating)
                .average()
                .orElse(0.0);

        return filtered.stream()
                .sorted(Comparator.comparingDouble(
                        (PlaceCandidateDTO p) -> bayesianScore(p.rating(), p.userRatingCount(), poolAverageRating)
                ).reversed())
                .limit(resolvedMax)
                .toList();
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
