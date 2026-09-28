package com.team.cultureevents.features.events.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.seoul.SeoulEventCache;
import com.team.cultureevents.features.seoul.SeoulOpenApiClient;
import com.team.cultureevents.features.seoul.domain.SeoulEvent;
import com.team.cultureevents.features.summary.domain.entity.AiSummaryEntity;
import com.team.cultureevents.features.summary.repository.AiSummaryRepository;
import com.team.cultureevents.features.views.domain.entity.EventViewEntity;
import com.team.cultureevents.features.views.repository.EventViewRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventServiceTest {

    private final SeoulOpenApiClient client = mock(SeoulOpenApiClient.class);
    private final SeoulEventCache cache = mock(SeoulEventCache.class);
    private final EventViewRepository eventViewRepository = mock(EventViewRepository.class);
    private final AiSummaryRepository aiSummaryRepository = mock(AiSummaryRepository.class);
    private final EventService service = new EventService(client, cache, eventViewRepository, aiSummaryRepository);

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
    void detailFollowsAliasIdOfMergedDuplicate() {
        SeoulEvent merged = new SeoulEvent("https://culture.seoul.go.kr/old", "빅무브", "공연", "중구", "DDP",
                "2026-10-01", "2026-10-20", "", "", "", "", 37.56, 127.01, List.of("https://culture.seoul.go.kr/new"));
        when(cache.getIfFresh()).thenReturn(List.of(merged));

        // 합쳐져 사라진 주소로 조회해도 대표 행사(대표 eventId)가 나온다
        assertEquals("https://culture.seoul.go.kr/old", service.getDetail("https://culture.seoul.go.kr/new").eventId());
        assertEquals("https://culture.seoul.go.kr/old", service.getDetail("https://culture.seoul.go.kr/old").eventId());
    }

    @Test
    void detailKeepsViewCountAndSummaryStoredUnderAlias() {
        SeoulEvent merged = new SeoulEvent("https://culture.seoul.go.kr/old", "빅무브", "공연", "중구", "DDP",
                "2026-10-01", "2026-10-20", "", "", "", "", 37.56, 127.01,
                List.of("https://culture.seoul.go.kr/new"));
        when(cache.getIfFresh()).thenReturn(List.of(merged));
        when(eventViewRepository.findById("https://culture.seoul.go.kr/old")).thenReturn(Optional.empty());
        when(eventViewRepository.findById("https://culture.seoul.go.kr/new"))
                .thenReturn(Optional.of(new EventViewEntity("https://culture.seoul.go.kr/new", 4)));
        when(aiSummaryRepository.findById("https://culture.seoul.go.kr/old")).thenReturn(Optional.empty());
        when(aiSummaryRepository.findById("https://culture.seoul.go.kr/new"))
                .thenReturn(Optional.of(new AiSummaryEntity("https://culture.seoul.go.kr/new", "별칭 소개",
                        Instant.parse("2026-09-01T00:00:00Z"))));

        var detail = service.getDetail("https://culture.seoul.go.kr/old");

        assertEquals(4, detail.viewCount());
        assertEquals("별칭 소개", detail.summary());
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

    @Test
    void detailIncludesSavedSummaryWhenPresent() {
        when(cache.getIfFresh()).thenReturn(List.of(event("id-1", "전시")));
        when(aiSummaryRepository.findById("id-1"))
                .thenReturn(Optional.of(new AiSummaryEntity("id-1", "저장된 소개", Instant.parse("2026-09-01T00:00:00Z"))));

        assertEquals("저장된 소개", service.getDetail("id-1").summary());
    }

    @Test
    void detailSummaryIsNullWhenNotGeneratedYet() {
        when(cache.getIfFresh()).thenReturn(List.of(event("id-1", "전시")));
        when(aiSummaryRepository.findById("id-1")).thenReturn(Optional.empty());

        assertEquals(null, service.getDetail("id-1").summary());
    }

    @Test
    void listIncludesSavedViewCountAndDefaultsToZeroWithoutIncrementing() {
        when(cache.getIfFresh()).thenReturn(List.of(event("viewed", "전시"), event("new", "공연")));
        when(eventViewRepository.findAll()).thenReturn(List.of(new EventViewEntity("viewed", 17)));

        var result = service.list(List.of(), List.of(), List.of(), null, null, null, 0, 20);

        assertEquals(17, result.events().stream()
                .filter(event -> event.eventId().equals("viewed"))
                .findFirst().orElseThrow().viewCount());
        assertEquals(0, result.events().stream()
                .filter(event -> event.eventId().equals("new"))
                .findFirst().orElseThrow().viewCount());
    }

    @Test
    void listIncludesViewCountsStoredUnderMergedAliases() {
        SeoulEvent merged = new SeoulEvent("canonical", "합쳐진 행사", "전시", "중구", "DDP",
                "2026-10-01", "2026-10-20", "", "", "", "", null, null,
                List.of("alias"));
        when(cache.getIfFresh()).thenReturn(List.of(merged));
        when(eventViewRepository.findAll()).thenReturn(List.of(
                new EventViewEntity("canonical", 1),
                new EventViewEntity("alias", 50)));

        var result = service.list(List.of(), List.of(), List.of(), null, null, null, 0, 20);

        assertEquals(51, result.events().get(0).viewCount());
    }

    private static SeoulEvent event(String id, String category) {
        return new SeoulEvent(id, "테스트 행사", category, "마포구", "장소", "2026-10-10",
                "2026-10-12", "무료", "서울시", id, "");
    }
}
