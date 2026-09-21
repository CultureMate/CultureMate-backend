package com.team.cultureevents.features.commons.util;

import com.team.cultureevents.features.commons.handler.BusinessException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** 서울시 행사 기간 파싱·겹침 판정. EventService / FavoriteService가 같이 쓴다. */
public final class EventDates {

    private EventDates() {}

    public record ClosedRange(LocalDate start, LocalDate end) {
        public boolean contains(LocalDate day) {
            return !day.isBefore(start) && !day.isAfter(end);
        }

        public boolean overlaps(LocalDate from, LocalDate to) {
            return (from == null || !end.isBefore(from)) && (to == null || !start.isAfter(to));
        }
    }

    public static LocalDate parseFlexible(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            return LocalDate.parse(value.substring(0, Math.min(10, value.length())));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static LocalDate parseIsoOrBadRequest(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw BusinessException.badRequest("날짜 형식이 올바르지 않습니다. yyyy-MM-dd");
        }
    }

    public static Optional<ClosedRange> closedRange(LocalDate start, LocalDate end) {
        if (start != null && end != null && start.isAfter(end)) {
            return Optional.empty();
        }
        if (start == null && end == null) {
            return Optional.empty();
        }
        LocalDate from = start == null ? end : start;
        LocalDate to = end == null ? start : end;
        return Optional.of(new ClosedRange(from, to));
    }

    public static Optional<ClosedRange> closedRange(String startRaw, String endRaw) {
        return closedRange(parseFlexible(startRaw), parseFlexible(endRaw));
    }

    public static boolean inverted(String startRaw, String endRaw) {
        LocalDate start = parseFlexible(startRaw);
        LocalDate end = parseFlexible(endRaw);
        return start != null && end != null && start.isAfter(end);
    }
}
