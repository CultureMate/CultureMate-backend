package com.team.cultureevents.features.courses.domain.dto;

import com.team.cultureevents.features.courses.domain.entity.CourseStopEntity;
import com.team.cultureevents.features.courses.domain.entity.CourseStopType;

public record CoursePreviewStopDTO(
        int stopOrder,
        String type,
        String eventId,
        String placeId,
        String name,
        String imageUrl
) {
    public static CoursePreviewStopDTO from(CourseStopEntity stop) {
        boolean event = stop.getType() == CourseStopType.EVENT;

        return new CoursePreviewStopDTO(
                stop.getStopOrder(),
                stop.getType().apiValue(),
                stop.getEventId(),
                stop.getPlaceId(),
                event ? stop.getEventTitle() : null,
                event ? stop.getEventImageUrl() : null
        );
    }
}