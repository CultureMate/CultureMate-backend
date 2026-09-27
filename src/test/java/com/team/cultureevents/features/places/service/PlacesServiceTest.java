package com.team.cultureevents.features.places.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.places.GooglePlacesClient;
import com.team.cultureevents.features.places.domain.dto.PlaceCandidateDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlacesServiceTest {

    private final GooglePlacesClient placesClient = mock(GooglePlacesClient.class);
    private final PlacesService service = new PlacesService(placesClient);

    private static PlaceCandidateDTO candidate(String name, Double rating, Integer reviewCount) {
        return new PlaceCandidateDTO("id-" + name, name, "주소", rating, reviewCount, 37.5, 127.0, "url");
    }

    @Test
    void missingCoordinatesIsBadRequest() {
        assertThrows(BusinessException.class, () -> service.recommendNearby(null, 127.0, null, null, null));
        assertThrows(BusinessException.class, () -> service.recommendNearby(37.5, null, null, null, null));
    }

    @Test
    void outOfKoreaRangeIsBadRequest() {
        assertThrows(BusinessException.class, () -> service.recommendNearby(10.0, 127.0, null, null, null));
        assertThrows(BusinessException.class, () -> service.recommendNearby(37.5, 200.0, null, null, null));
    }

    @Test
    void alwaysRequestsFullPoolFromGoogleRegardlessOfMaxResults() {
        when(placesClient.searchNearby(eq(37.5), eq(127.0), eq(List.of("restaurant")), eq(300), eq(20)))
                .thenReturn(List.of());

        service.recommendNearby(37.5, 127.0, List.of("restaurant"), 300, 3);

        verify(placesClient).searchNearby(37.5, 127.0, List.of("restaurant"), 300, 20);
    }

    @Test
    void sortsByBayesianScoreAndAppliesMinimumReviewFloor() {
        when(placesClient.searchNearby(eq(37.5), eq(127.0), eq(List.of("restaurant", "cafe")), eq(500), eq(20)))
                .thenReturn(List.of(
                        candidate("리뷰적은고평점", 5.0, 2),     // 리뷰 5개 미만 -> 제외
                        candidate("평점없음", null, 100),         // 평점 없음 -> 제외
                        candidate("2등", 4.5, 300),
                        candidate("1등", 4.5, 500),               // 평점 동률이면 리뷰 많은 쪽 점수가 더 높음
                        candidate("3등", 4.2, 50)
                ));

        List<PlaceCandidateDTO> result = service.recommendNearby(37.5, 127.0, null, null, null);

        assertEquals(3, result.size());
        assertEquals("1등", result.get(0).name());
        assertEquals("2등", result.get(1).name());
        assertEquals("3등", result.get(2).name());
    }

    @Test
    void limitsReturnedResultsToRequestedMaxResults() {
        when(placesClient.searchNearby(eq(37.5), eq(127.0), eq(List.of("restaurant", "cafe")), eq(500), eq(20)))
                .thenReturn(List.of(
                        candidate("가", 4.9, 100),
                        candidate("나", 4.8, 100),
                        candidate("다", 4.7, 100)
                ));

        List<PlaceCandidateDTO> result = service.recommendNearby(37.5, 127.0, null, null, 2);

        assertEquals(2, result.size());
    }

    // --- bayesianScore 공식 자체를 직접 검증 (실제 배치 없이, 값만 넣어서) ---

    @Test
    void bayesianScorePullsLowReviewRatingTowardPoolAverage() {
        // 리뷰 5개짜리 만점(5.0)보다, 리뷰 1000개짜리 4.3점이 더 신뢰도 높게 평가될 수 있다.
        // (주변 평균이 3.5로 낮은 동네라면, 리뷰 적은 5.0은 "우연히 잘 나온 값"일 수 있다고 보정한다)
        double lowReviewHighRating = PlacesService.bayesianScore(5.0, 5, 3.5);
        double highReviewGoodRating = PlacesService.bayesianScore(4.3, 1000, 3.5);

        assertTrue(highReviewGoodRating > lowReviewHighRating,
                "리뷰 많은 4.3점이 리뷰 적은 5.0점보다 높게 평가돼야 함");
    }

    @Test
    void bayesianScoreBarelyMovesWhenReviewCountIsVeryHigh() {
        double score = PlacesService.bayesianScore(4.0, 100_000, 3.0);

        assertEquals(4.0, score, 0.01); // 리뷰가 충분히 많으면 거의 원래 평점 그대로 인정됨
    }
}
