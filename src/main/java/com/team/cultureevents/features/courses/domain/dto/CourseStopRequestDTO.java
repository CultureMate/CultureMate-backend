package com.team.cultureevents.features.courses.domain.dto;

public record CourseStopRequestDTO(
        String type,
        String eventId,
        String placeId
) {
}
