package com.team.cultureevents.features.courses.domain.entity;

import com.team.cultureevents.features.commons.handler.BusinessException;

import java.util.Locale;

public enum CourseStopType {
    EVENT,
    CAFE,
    RESTAURANT;

    public static CourseStopType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BusinessException.badRequest("stop.type은 event, cafe, restaurant 중 하나여야 합니다.");
        }
        try {
            return CourseStopType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("stop.type은 event, cafe, restaurant 중 하나여야 합니다.");
        }
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
