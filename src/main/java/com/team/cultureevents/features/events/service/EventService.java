package com.team.cultureevents.features.events.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.commons.util.EventDates;
import com.team.cultureevents.features.commons.util.EventDates.ClosedRange;
import com.team.cultureevents.features.events.domain.dto.EventDetailResponseDTO;
import com.team.cultureevents.features.events.domain.dto.EventListResponseDTO;
import com.team.cultureevents.features.events.domain.dto.EventSummaryResponseDTO;
import com.team.cultureevents.features.seoul.SeoulEventCache;
import com.team.cultureevents.features.seoul.SeoulOpenApiClient;
import com.team.cultureevents.features.seoul.domain.SeoulEvent;
import com.team.cultureevents.features.views.repository.EventViewRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class EventService {

    private final SeoulOpenApiClient client;
    private final SeoulEventCache cache;
    private final EventViewRepository eventViewRepository;

    public EventService(SeoulOpenApiClient client, SeoulEventCache cache, EventViewRepository eventViewRepository) {
        this.client = client;
        this.cache = cache;
        this.eventViewRepository = eventViewRepository;
    }

    public EventListResponseDTO list(List<String> districts, List<String> categories, List<String> dates) {
        return list(districts, categories, dates, null, null, null, null, null);
    }

    public EventListResponseDTO list(List<String> districts, List<String> categories, List<String> dates,
                                     String keyword, String from, String to, Integer page, Integer size) {
        List<LocalDate> filterDates = parseDates(dates);
        LocalDate fromDate = EventDates.parseIsoOrBadRequest(from);
        LocalDate toDate = EventDates.parseIsoOrBadRequest(to);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw BusinessException.badRequest("from은 to보다 늦을 수 없습니다.");
        }
        if (page != null && page < 0) {
            throw BusinessException.badRequest("page는 0 이상이어야 합니다.");
        }
        if (size != null && (size < 1 || size > 100)) {
            throw BusinessException.badRequest("size는 1~100이어야 합니다.");
        }

        Set<String> districtSet = normalizeSet(districts);
        Set<String> categorySet = normalizeSet(categories);
        String normalizedKeyword = norm(keyword);

        List<SeoulEvent> filtered = loadEvents().stream()
                .filter(e -> districtSet.isEmpty() || districtSet.contains(norm(e.district())))
                .filter(e -> categorySet.isEmpty() || categoryMatches(categorySet, e.category()))
                .filter(e -> dateMatches(filterDates, e))
                .filter(e -> rangeMatches(fromDate, toDate, e))
                .filter(e -> normalizedKeyword.isBlank()
                        || norm(e.title()).contains(normalizedKeyword)
                        || norm(e.place()).contains(normalizedKeyword))
                .sorted(Comparator.comparing(
                        (SeoulEvent e) -> EventDates.parseFlexible(e.startDate()),
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).thenComparing(SeoulEvent::title, Comparator.nullsLast(String::compareTo)))
                .toList();

        boolean paged = page != null || size != null;
        if (!paged) {
            return new EventListResponseDTO(filtered.size(), filtered.size(), null, null,
                    filtered.stream().map(this::toSummary).toList());
        }
        int actualPage = page == null ? 0 : page;
        int actualSize = size == null ? 20 : size;
        long first = (long) actualPage * actualSize;
        List<EventSummaryResponseDTO> items = first >= filtered.size() ? List.of()
                : filtered.subList((int) first, Math.min(filtered.size(), (int) first + actualSize))
                .stream().map(this::toSummary).toList();
        return new EventListResponseDTO(items.size(), filtered.size(), actualPage, actualSize, items);
    }

    public EventDetailResponseDTO getDetail(String rawEventId) {
        String eventId = rawEventId == null ? "" : rawEventId.trim();
        if (eventId.isBlank()) {
            throw BusinessException.badRequest("eventId가 필요합니다.");
        }
        return loadEvents().stream()
                .filter(e -> eventId.equals(e.eventId()))
                .findFirst()
                .map(this::toDetail)
                .orElseThrow(() -> BusinessException.notFound("존재하지 않는 행사입니다."));
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

    private EventSummaryResponseDTO toSummary(SeoulEvent e) {
        boolean invalidPeriod = EventDates.inverted(e.startDate(), e.endDate());
        return new EventSummaryResponseDTO(
                e.eventId(), e.title(), e.category(), e.district(),
                e.place(), invalidPeriod ? "" : e.startDate(), invalidPeriod ? "" : e.endDate(),
                emptyToBlank(e.imageUrl()), e.latitude(), e.longitude()
        );
    }

    private EventDetailResponseDTO toDetail(SeoulEvent e) {
        boolean invalidPeriod = EventDates.inverted(e.startDate(), e.endDate());
        int viewCount = eventViewRepository.findById(e.eventId())
                .map(v -> v.getViewCount())
                .orElse(0);
        return new EventDetailResponseDTO(
                e.eventId(), e.title(), e.category(), e.district(), e.place(),
                invalidPeriod ? "" : e.startDate(), invalidPeriod ? "" : e.endDate(),
                emptyToBlank(e.fee()), emptyToBlank(e.organization()),
                emptyToBlank(e.originalUrl()), emptyToBlank(e.imageUrl()), viewCount,
                e.latitude(), e.longitude()
        );
    }

    private static String emptyToBlank(String v) {
        return v == null ? "" : v;
    }

    private static Set<String> normalizeSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> set = new LinkedHashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                set.add(norm(v));
            }
        }
        return set;
    }

    private static String norm(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean categoryMatches(Set<String> wanted, String category) {
        String c = norm(category);
        if (c.isBlank()) {
            return false;
        }
        for (String w : wanted) {
            if (c.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static boolean dateMatches(List<LocalDate> dates, SeoulEvent e) {
        if (dates.isEmpty()) {
            return true;
        }
        Optional<ClosedRange> range = EventDates.closedRange(e.startDate(), e.endDate());
        return range.isPresent() && dates.stream().anyMatch(range.get()::contains);
    }

    private static boolean rangeMatches(LocalDate from, LocalDate to, SeoulEvent e) {
        if (from == null && to == null) {
            return true;
        }
        return EventDates.closedRange(e.startDate(), e.endDate())
                .map(range -> range.overlaps(from, to))
                .orElse(false);
    }

    private static List<LocalDate> parseDates(List<String> dates) {
        if (dates == null || dates.isEmpty()) {
            return List.of();
        }
        List<LocalDate> result = new ArrayList<>();
        for (String raw : dates) {
            LocalDate parsed = EventDates.parseIsoOrBadRequest(raw);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }
}
