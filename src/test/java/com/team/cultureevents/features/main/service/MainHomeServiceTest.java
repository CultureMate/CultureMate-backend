package com.team.cultureevents.features.main.service;

import com.team.cultureevents.features.seoul.SeoulEventCache;
import com.team.cultureevents.features.seoul.SeoulOpenApiClient;
import com.team.cultureevents.features.seoul.domain.SeoulEvent;
import com.team.cultureevents.features.views.repository.EventViewRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MainHomeServiceTest {

    private final SeoulOpenApiClient client = mock(SeoulOpenApiClient.class);
    private final SeoulEventCache cache = mock(SeoulEventCache.class);
    private final EventViewRepository views = mock(EventViewRepository.class);
    private final MainHomeService service = new MainHomeService(client, cache, views);

    @Test
    void upcomingFiltersByDistrictAndExcludesAlreadyStarted() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        when(cache.getIfFresh()).thenReturn(List.of(
                event("started", "마포구", today.minusDays(30), today.plusDays(30)),
                event("mapo-later", "마포구", today.plusDays(5), today.plusDays(6)),
                event("mapo-soon", "마포구", today.plusDays(1), today.plusDays(2)),
                event("gangnam", "강남구", today.plusDays(2), today.plusDays(3))
        ));

        var mapo = service.upcomingEvents("마포구", 10);
        assertEquals(List.of("mapo-soon", "mapo-later"), mapo.events().stream().map(e -> e.eventId()).toList());
        assertEquals("마포구", mapo.district());
        assertEquals(1, mapo.events().get(0).dDay());

        var all = service.upcomingEvents(null, 10);
        assertEquals(3, all.events().size());
        assertNull(all.district());
    }

    private static SeoulEvent event(String id, String district, LocalDate start, LocalDate end) {
        return new SeoulEvent(id, id, "전시", district, "장소", start.toString(), end.toString(),
                "", "", "", "");
    }
}
