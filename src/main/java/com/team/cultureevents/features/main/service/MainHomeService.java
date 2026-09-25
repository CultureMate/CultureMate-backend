package com.team.cultureevents.features.main.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.commons.util.EventDates;
import com.team.cultureevents.features.main.domain.dto.HomeEventItemDTO;
import com.team.cultureevents.features.main.domain.dto.HomeEventsResponseDTO;
import com.team.cultureevents.features.seoul.SeoulEventCache;
import com.team.cultureevents.features.seoul.SeoulOpenApiClient;
import com.team.cultureevents.features.seoul.domain.SeoulEvent;
import com.team.cultureevents.features.views.domain.entity.EventViewEntity;
import com.team.cultureevents.features.views.repository.EventViewRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 홈 FR-07용. FE가 기대하는 GET /api/main/hot-events · upcoming-events.
 * 거주지 필터는 아직 쿠키 연동 전이므로 서울 전체 기준으로 내려준다.
 */
@Service
public class MainHomeService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SeoulOpenApiClient client;
    private final SeoulEventCache cache;
    private final EventViewRepository eventViewRepository;

    public MainHomeService(SeoulOpenApiClient client, SeoulEventCache cache,
                           EventViewRepository eventViewRepository) {
        this.client = client;
        this.cache = cache;
        this.eventViewRepository = eventViewRepository;
    }

    public HomeEventsResponseDTO hotEvents(Integer limit) {
        int lim = normalizeLimit(limit);
        Map<String, Integer> views = viewMap();
        List<HomeEventItemDTO> events = loadEvents().stream()
                .map(e -> toItem(e, views))
                .sorted(Comparator
                        .comparingInt(HomeEventItemDTO::viewCount).reversed()
                        .thenComparing(HomeEventItemDTO::title, Comparator.nullsLast(String::compareTo)))
                .limit(lim)
                .toList();
        return new HomeEventsResponseDTO(events);
    }

    public HomeEventsResponseDTO upcomingEvents(Integer limit) {
        int lim = normalizeLimit(limit);
        LocalDate today = LocalDate.now(SEOUL);
        Map<String, Integer> views = viewMap();
        List<HomeEventItemDTO> events = loadEvents().stream()
                .filter(e -> notEnded(e, today))
                .map(e -> toItem(e, views))
                .sorted(Comparator
                        .comparing(HomeEventItemDTO::startDate, Comparator.nullsLast(String::compareTo))
                        .thenComparing(HomeEventItemDTO::title, Comparator.nullsLast(String::compareTo)))
                .limit(lim)
                .toList();
        return new HomeEventsResponseDTO(events);
    }

    private Map<String, Integer> viewMap() {
        return eventViewRepository.findAll().stream()
                .collect(Collectors.toMap(EventViewEntity::getEventId, EventViewEntity::getViewCount, (a, b) -> a));
    }

    private synchronized List<SeoulEvent> loadEvents() {
        List<SeoulEvent> cached = cache.getIfFresh();
        if (cached != null) {
            return cached;
        }
        List<SeoulEvent> fresh = client.fetchAll();
        cache.put(fresh);
        return fresh;
    }

    private HomeEventItemDTO toItem(SeoulEvent e, Map<String, Integer> views) {
        boolean invalidPeriod = EventDates.inverted(e.startDate(), e.endDate());
        String start = invalidPeriod ? "" : nullToEmpty(e.startDate());
        String end = invalidPeriod ? "" : nullToEmpty(e.endDate());
        LocalDate today = LocalDate.now(SEOUL);
        LocalDate startDate = EventDates.parseFlexible(start);
        Integer dDay = startDate == null ? null : (int) ChronoUnit.DAYS.between(today, startDate);
        return new HomeEventItemDTO(
                e.eventId(),
                e.title(),
                e.category(),
                e.district(),
                e.place(),
                start,
                end,
                nullToEmpty(e.imageUrl()),
                views.getOrDefault(e.eventId(), 0),
                dDay
        );
    }

    private static boolean notEnded(SeoulEvent e, LocalDate today) {
        LocalDate end = EventDates.parseFlexible(e.endDate());
        if (end != null) {
            return !end.isBefore(today);
        }
        LocalDate start = EventDates.parseFlexible(e.startDate());
        return start == null || !start.isBefore(today);
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 6;
        }
        if (limit < 1 || limit > 30) {
            throw BusinessException.badRequest("limit는 1~30이어야 합니다.");
        }
        return limit;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
