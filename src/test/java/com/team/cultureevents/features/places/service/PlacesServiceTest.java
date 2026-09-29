package com.team.cultureevents.features.places.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.events.domain.dto.EventDetailResponseDTO;
import com.team.cultureevents.features.events.service.EventService;
import com.team.cultureevents.features.places.GooglePlacesClient;
import com.team.cultureevents.features.places.domain.dto.PlaceCandidateDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlacesServiceTest {

    @Test
    void betweenBalancesQualityAndDetourAndExcludesDistantCandidates() {
        when(eventService.getDetail("a")).thenReturn(eventAt("a", 37.5, 127.0));
        when(eventService.getDetail("b")).thenReturn(eventAt("b", 37.5, 127.02));
        PlaceCandidateDTO onRoute = located("on", 3.5, 37.5, 127.01);
        PlaceCandidateDTO better = located("better", 4.9, 37.503, 127.01);
        PlaceCandidateDTO equalFarther = located("farther", 4.9, 37.506, 127.01);
        PlaceCandidateDTO excluded = located("excluded", 5.0, 37.6, 127.01);
        when(placesClient.searchNearby(anyDouble(), anyDouble(), eq(List.of("cafe")), anyInt(), eq(20), eq(1L)))
                .thenReturn(List.of(onRoute, equalFarther, excluded, better));
        var result = service.recommendBetweenEvents("a", "b", "cafe", 1L);
        assertEquals(List.of("better", "farther", "on"), result.stream().map(PlaceCandidateDTO::name).toList());
        double direct = PlacesService.haversineMeters(37.5, 127, 37.5, 127.02);
        for (var p : result) {
            double expected = Math.max(0, PlacesService.haversineMeters(37.5, 127, p.latitude(), p.longitude())
                    + PlacesService.haversineMeters(p.latitude(), p.longitude(), 37.5, 127.02) - direct);
            assertEquals(expected, p.detourMeters(), 0.001);
            assertTrue(p.detourMeters() <= Math.min(2000, Math.max(300, direct * 0.5)));
        }
    }

    @Test
    void coincidentEventsHaveFiniteScoresAnd300MeterDetourCap() {
        when(eventService.getDetail("a")).thenReturn(eventAt("a", 37.5, 127));
        when(eventService.getDetail("b")).thenReturn(eventAt("b", 37.5, 127));
        when(placesClient.searchNearby(anyDouble(), anyDouble(), eq(List.of("cafe")), anyInt(), eq(20), eq(1L)))
                .thenReturn(List.of(located("near", 4.5, 37.5001, 127), located("far", 5, 37.51, 127)));
        var result = service.recommendBetweenEvents("a", "b", "cafe", 1L);
        assertEquals(1, result.size());
        assertTrue(result.get(0).detourMeters() <= 300);
        assertTrue(Double.isFinite(result.get(0).recommendationScore()));
    }

    private static PlaceCandidateDTO located(String name, double rating, double lat, double lng) {
        return new PlaceCandidateDTO(name, name, "address", rating, 100, lat, lng, "url", null, null, "OPERATIONAL", true);
    }

    private final GooglePlacesClient placesClient = mock(GooglePlacesClient.class);
    private final EventService eventService = mock(EventService.class);
    private final PlacesService service = new PlacesService(placesClient, eventService);

    private static PlaceCandidateDTO candidate(String name, Double rating, Integer reviewCount) {
        return candidate(name, rating, reviewCount, "OPERATIONAL");
    }

    private static PlaceCandidateDTO candidate(String name, Double rating, Integer reviewCount, String businessStatus) {
        return new PlaceCandidateDTO("id-" + name, name, "주소", rating, reviewCount, 37.5, 127.0, "url", null, null,
                businessStatus, null);
    }

    private static EventDetailResponseDTO eventAt(String eventId, double lat, double lng) {
        return new EventDetailResponseDTO(eventId, "제목", "분류", "구", "장소",
                "2026-01-01", "2026-01-02", "", "", "", "", 0, lat, lng);
    }

    @Test
    void missingCoordinatesIsBadRequest() {
        assertThrows(BusinessException.class, () -> service.recommendNearby(null, 127.0, null, null, null, 1L));
        assertThrows(BusinessException.class, () -> service.recommendNearby(37.5, null, null, null, null, 1L));
    }

    @Test
    void outOfKoreaRangeIsBadRequest() {
        assertThrows(BusinessException.class, () -> service.recommendNearby(10.0, 127.0, null, null, null, 1L));
        assertThrows(BusinessException.class, () -> service.recommendNearby(37.5, 200.0, null, null, null, 1L));
    }

    @Test
    void alwaysRequestsFullPoolFromGoogleRegardlessOfMaxResults() {
        when(placesClient.searchNearby(eq(37.5), eq(127.0), eq(List.of("restaurant")), eq(300), eq(20), eq(1L)))
                .thenReturn(List.of());

        service.recommendNearby(37.5, 127.0, List.of("restaurant"), 300, 3, 1L);

        verify(placesClient).searchNearby(37.5, 127.0, List.of("restaurant"), 300, 20, 1L);
    }

    @Test
    void sortsByBayesianScoreAndAppliesMinimumReviewFloor() {
        when(placesClient.searchNearby(eq(37.5), eq(127.0), eq(List.of("restaurant", "cafe")), eq(500), eq(20), eq(1L)))
                .thenReturn(List.of(
                        candidate("리뷰적은고평점", 5.0, 2),     // 리뷰 5개 미만 -> 제외
                        candidate("평점없음", null, 100),         // 평점 없음 -> 제외
                        candidate("2등", 4.5, 300),
                        candidate("1등", 4.5, 500),               // 평점 동률이면 리뷰 많은 쪽 점수가 더 높음
                        candidate("3등", 4.2, 50)
                ));

        List<PlaceCandidateDTO> result = service.recommendNearby(37.5, 127.0, null, null, null, 1L);

        assertEquals(3, result.size());
        assertEquals("1등", result.get(0).name());
        assertEquals("2등", result.get(1).name());
        assertEquals("3등", result.get(2).name());
    }

    @Test
    void filtersOutPermanentlyOrTemporarilyClosedPlaces() {
        when(placesClient.searchNearby(eq(37.5), eq(127.0), eq(List.of("restaurant", "cafe")), eq(500), eq(20), eq(1L)))
                .thenReturn(List.of(
                        candidate("영구폐업", 4.9, 500, "CLOSED_PERMANENTLY"),
                        candidate("임시휴업", 4.9, 500, "CLOSED_TEMPORARILY"),
                        candidate("정상영업", 4.0, 50, "OPERATIONAL")
                ));

        List<PlaceCandidateDTO> result = service.recommendNearby(37.5, 127.0, null, null, null, 1L);

        assertEquals(1, result.size());
        assertEquals("정상영업", result.get(0).name());
    }

    @Test
    void limitsReturnedResultsToRequestedMaxResults() {
        when(placesClient.searchNearby(eq(37.5), eq(127.0), eq(List.of("restaurant", "cafe")), eq(500), eq(20), eq(1L)))
                .thenReturn(List.of(
                        candidate("가", 4.9, 100),
                        candidate("나", 4.8, 100),
                        candidate("다", 4.7, 100)
                ));

        List<PlaceCandidateDTO> result = service.recommendNearby(37.5, 127.0, null, null, 2, 1L);

        assertEquals(2, result.size());
    }

    // --- 두 행사 사이 구간 검색 ---

    @Test
    void closeEventsSearchOnceAtMidpointWithHalfDistanceAsRadius() {
        when(eventService.getDetail("e1")).thenReturn(eventAt("e1", 37.0, 127.0));
        when(eventService.getDetail("e2")).thenReturn(eventAt("e2", 37.0, 127.01));
        when(placesClient.searchNearby(anyDouble(), anyDouble(), eq(List.of("cafe")), anyInt(), eq(20), eq(1L)))
                .thenReturn(List.of());

        service.recommendBetweenEvents("e1", "e2", "cafe", 1L);

        double expectedDistance = PlacesService.haversineMeters(37.0, 127.0, 37.0, 127.01);
        assertTrue(expectedDistance <= PlacesService.MIDPOINT_SEARCH_MAX_DISTANCE_METERS);
        int expectedRadius = (int) Math.round(expectedDistance / 2);

        ArgumentCaptor<Double> lat = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> lng = ArgumentCaptor.forClass(Double.class);
        verify(placesClient, times(1)).searchNearby(lat.capture(), lng.capture(), eq(List.of("cafe")), eq(expectedRadius), eq(20), eq(1L));
        verify(placesClient, times(1)).searchNearby(anyDouble(), anyDouble(), any(), anyInt(), anyInt(), anyLong());

        // double은 이진 부동소수점이라 (127.0 + 127.01) / 2 가 정확히 127.005가 아닐 수 있다.
        // 좌표는 오차 허용 범위로 비교한다.
        assertEquals(37.0, lat.getValue(), 1e-9);
        assertEquals(127.005, lng.getValue(), 1e-9);
    }

    @Test
    void distantEventsSearchNearEachEventInsteadOfMidpoint() {
        when(eventService.getDetail("e1")).thenReturn(eventAt("e1", 37.5, 127.0));
        when(eventService.getDetail("e2")).thenReturn(eventAt("e2", 37.5, 127.03));
        when(placesClient.searchNearby(anyDouble(), anyDouble(), eq(List.of("cafe")), anyInt(), eq(20), eq(1L)))
                .thenReturn(List.of());

        service.recommendBetweenEvents("e1", "e2", "cafe", 1L);

        verify(placesClient).searchNearby(37.5, 127.0, List.of("cafe"), PlacesService.NEAR_EVENT_RADIUS_METERS, 20, 1L);
        verify(placesClient).searchNearby(37.5, 127.03, List.of("cafe"), PlacesService.NEAR_EVENT_RADIUS_METERS, 20, 1L);
        verify(placesClient, times(2)).searchNearby(anyDouble(), anyDouble(), any(), anyInt(), anyInt(), anyLong());
    }

    @Test
    void distantEventsAlternateCandidatesNearEachEventAndDropDuplicatesAndBacktracking() {
        when(eventService.getDetail("e1")).thenReturn(eventAt("e1", 37.5, 127.0));
        when(eventService.getDetail("e2")).thenReturn(eventAt("e2", 37.5, 127.03));
        PlaceCandidateDTO duplicate = located("dup", 4.0, 37.5, 127.027);
        when(placesClient.searchNearby(eq(37.5), eq(127.0), eq(List.of("cafe")), anyInt(), eq(20), eq(1L)))
                .thenReturn(List.of(
                        located("a1", 4.9, 37.5, 127.002),
                        located("a2", 4.8, 37.5, 127.003),
                        located("a3", 4.7, 37.5, 127.004),
                        located("behindA", 5.0, 37.5, 126.992),
                        duplicate));
        when(placesClient.searchNearby(eq(37.5), eq(127.03), eq(List.of("cafe")), anyInt(), eq(20), eq(1L)))
                .thenReturn(List.of(
                        located("b1", 4.6, 37.5, 127.028),
                        located("b2", 4.5, 37.5, 127.026),
                        duplicate));

        var result = service.recommendBetweenEvents("e1", "e2", "cafe", 1L);

        assertEquals(List.of("a1", "b1", "a2", "b2", "a3", "dup"),
                result.stream().map(PlaceCandidateDTO::name).toList());
        assertEquals(List.of("e1", "e2", "e1", "e2", "e1", "e2"),
                result.stream().map(PlaceCandidateDTO::nearEventId).toList());
    }

    @Test
    void alternationFillsFromRemainingSideWhenOneSideRunsOut() {
        var ranked = List.of(
                ranked("b1", 4.9, "e2"), ranked("a1", 4.8, "e1"), ranked("b2", 4.7, "e2"), ranked("b3", 4.6, "e2"));

        var result = PlacesService.alternateByNearEvent(ranked, "e1", 3);

        assertEquals(List.of("b1", "a1", "b2"), result.stream().map(PlaceCandidateDTO::name).toList());
    }

    private static PlaceCandidateDTO ranked(String name, double score, String nearEventId) {
        return located(name, 4.0, 37.5, 127.0).ranked(0, score).near(nearEventId);
    }

    @Test
    void recommendBetweenEventsRejectsWhenCoordinatesMissing() {
        when(eventService.getDetail("e1")).thenReturn(eventAt("e1", 37.0, 127.0));
        when(eventService.getDetail("e2")).thenReturn(new EventDetailResponseDTO(
                "e2", "제목", "분류", "구", "장소", "2026-01-01", "2026-01-02", "", "", "", "", 0));

        assertThrows(BusinessException.class, () -> service.recommendBetweenEvents("e1", "e2", "cafe", 1L));
    }

    @Test
    void recommendBetweenEventsRequiresType() {
        assertThrows(BusinessException.class, () -> service.recommendBetweenEvents("e1", "e2", null, 1L));
    }

    @Test
    void haversineMeasuresOneDegreeLatitudeCorrectly() {
        // 경도 차이가 0이면 하버사인 공식이 (지구 반지름 * 라디안 각도)로 정확히 떨어진다.
        double meters = PlacesService.haversineMeters(37.0, 127.0, 38.0, 127.0);

        assertEquals(6_371_000 * Math.toRadians(1.0), meters, 1.0);
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

    @Test
    void resolvePhotoUriRequiresName() {
        assertThrows(BusinessException.class, () -> service.resolvePhotoUri(null, 400, 1L));
        assertThrows(BusinessException.class, () -> service.resolvePhotoUri("  ", 400, 1L));
    }

    @Test
    void resolvePhotoUriDelegatesToClientWithDefaultWidth() {
        when(placesClient.resolvePhotoUri("places/p1/photos/abc", 400, 1L)).thenReturn("https://lh3.googleusercontent.com/abc");

        String uri = service.resolvePhotoUri("places/p1/photos/abc", null, 1L);

        assertEquals("https://lh3.googleusercontent.com/abc", uri);
    }
}
