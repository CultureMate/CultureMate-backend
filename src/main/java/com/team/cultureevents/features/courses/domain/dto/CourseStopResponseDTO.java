package com.team.cultureevents.features.courses.domain.dto;

import com.team.cultureevents.features.courses.domain.entity.CourseStopEntity;

import java.time.LocalDate;

public record CourseStopResponseDTO(
        int stopOrder,
        String type,
        String eventId,
        String placeId,
        String eventTitle,
        String eventCategory,
        String eventDistrict,
        String eventPlace,
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        String eventImageUrl,
        Double eventLatitude,
        Double eventLongitude
) {
    public static CourseStopResponseDTO from(CourseStopEntity stop) {
        return new CourseStopResponseDTO(
                stop.getStopOrder(),
                stop.getType().apiValue(),
                stop.getEventId(),
                stop.getPlaceId(),
                stop.getEventTitle(),
                stop.getEventCategory(),
                stop.getEventDistrict(),
                stop.getEventPlace(),
                stop.getEventStartDate(),
                stop.getEventEndDate(),
                stop.getEventImageUrl(),
                stop.getEventLatitude(),
                stop.getEventLongitude()
        );
    }
}
