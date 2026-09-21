package com.team.cultureevents.features.events.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.seoul.SeoulEventCache;
import com.team.cultureevents.features.seoul.SeoulOpenApiClient;
import com.team.cultureevents.features.seoul.domain.SeoulEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventServiceTest {

    private final SeoulOpenApiClient client = mock(SeoulOpenApiClient.class);
    private final SeoulEventCache cache = mock(SeoulEventCache.class);
    private final EventService service = new EventService(client, cache);

    @Test
    void blankSourceCategoryDoesNotMatchSelectedCategory() {
        when(cache.getIfFresh()).thenReturn(List.of(event("id-1", "")));

        assertEquals(0, service.list(List.of(), List.of("전시"), List.of()).count());
    }

    @Test
    void detailRequiresExactIdAndPreservesPlusAndPercent() {
        String id = "https://culture.seoul.go.kr/event?code=A+B%25&menu=1";
        when(cache.getIfFresh()).thenReturn(List.of(event(id, "전시")));

        assertEquals(id, service.getDetail(id).eventId());
        assertThrows(BusinessException.class, () -> service.getDetail("https://culture.seoul.go.kr/event?code=A"));
    }

    @Test
    void prototypeKeywordAndDateRangeFindOverlappingEvent() {
        SeoulEvent wanted = new SeoulEvent("event-1", "마포 가을 전시", "전시/미술", "마포구",
                "문화회관", "2026-09-20", "2026-09-25", "무료", "서울시", "https://example.com/1", "https://example.com/image.jpg");
        SeoulEvent other = new SeoulEvent("event-2", "다른 행사", "전시/미술", "마포구",
                "문화회관", "2026-10-01", "2026-10-02", "무료", "서울시", "", "");
        when(cache.getIfFresh()).thenReturn(List.of(other, wanted));

        var result = service.list(List.of("마포구"), List.of("전시"), List.of(),
                "가을", "2026-09-22", "2026-09-30", 0, 20);

        assertEquals(1, result.totalCount());
        assertEquals(1, result.count());
        assertEquals("event-1", result.events().get(0).eventId());
        assertEquals("https://example.com/image.jpg", result.events().get(0).imageUrl());
        assertEquals("https://example.com/image.jpg", service.getDetail("event-1").imageUrl());
    }

    @Test
    void paginationPreservesTotalAndRejectsBackwardsRange() {
        when(cache.getIfFresh()).thenReturn(List.of(
                event("id-1", "전시"), event("id-2", "전시"), event("id-3", "전시")));

        var secondPage = service.list(List.of(), List.of(), List.of(), null, null, null, 1, 2);
        assertEquals(3, secondPage.totalCount());
        assertEquals(1, secondPage.count());
        assertEquals(1, secondPage.page());
        assertEquals("id-3", secondPage.events().get(0).eventId());
        assertThrows(BusinessException.class, () -> service.list(List.of(), List.of(), List.of(),
                null, "2026-10-12", "2026-10-10", 0, 20));
        assertTrue(service.list(List.of(), List.of(), List.of(), null, null, null, 2, 2).events().isEmpty());
    }

    @Test
    void invertedSourceDatesAreNotShownOrMatchedAsARealPeriod() {
        SeoulEvent bad = new SeoulEvent("bad-period", "COLD FEET [No Filter]", "전시/미술", "마포구",
                "전시공간", "2026-09-25", "2026-09-15", "무료", "기타", "https://example.com", "");
        when(cache.getIfFresh()).thenReturn(List.of(bad));

        assertEquals(0, service.list(List.of(), List.of(), List.of(), null,
                "2026-09-21", null, 0, 20).totalCount());
        assertEquals(0, service.list(List.of(), List.of(), List.of("2026-09-25")).totalCount());
        var detail = service.getDetail("bad-period");
        assertEquals("", detail.startDate());
        assertEquals("", detail.endDate());
        assertEquals("", service.list(List.of(), List.of(), List.of()).events().get(0).startDate());
    }

    private static SeoulEvent event(String id, String category) {
        return new SeoulEvent(id, "테스트 행사", category, "마포구", "장소", "2026-10-10",
                "2026-10-12", "무료", "서울시", id, "");
    }
}
