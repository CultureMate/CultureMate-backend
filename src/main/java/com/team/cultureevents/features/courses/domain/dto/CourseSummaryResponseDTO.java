package com.team.cultureevents.features.courses.domain.dto;

import com.team.cultureevents.features.courses.domain.entity.CourseEntity;
import com.team.cultureevents.features.courses.domain.entity.CourseStopEntity;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record CourseSummaryResponseDTO(
        Long courseId,
        String title,
        boolean favorited,
        Instant favoritedAt,
        long version,
        int stopCount,
        String firstEventTitle,
        String firstEventImageUrl,
        List<CoursePreviewStopDTO> previewStops,
        boolean shared,
        Instant createdAt,
        Instant updatedAt
) {
    private static final int MAX_PREVIEW_STOPS = 4;

    public static CourseSummaryResponseDTO from(CourseEntity course) {
        List<CoursePreviewStopDTO> previewStops = course.getStops().stream()
                .sorted(Comparator.comparingInt(CourseStopEntity::getStopOrder))
                .limit(MAX_PREVIEW_STOPS)
                .map(CoursePreviewStopDTO::from)
                .toList();

        return new CourseSummaryResponseDTO(
                course.getCourseId(),
                course.getTitle(),
                course.isFavorited(),
                course.getFavoritedAt(),
                course.getContentVersion(),
                course.getStopCount(),
                course.getFirstEventTitle(),
                course.getFirstEventImageUrl(),
                previewStops,
                course.getShareId() != null,
                course.getCreatedAt(),
                course.getUpdatedAt()
        );
    }
}