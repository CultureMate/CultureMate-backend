package com.team.cultureevents.features.courses.domain.dto;

import com.team.cultureevents.features.courses.domain.entity.CourseEntity;

import java.time.Instant;

public record CourseSummaryResponseDTO(
        Long courseId,
        String title,
        boolean favorited,
        Instant favoritedAt,
        long version,
        int stopCount,
        String firstEventTitle,
        String firstEventImageUrl,
        boolean shared,
        Instant createdAt,
        Instant updatedAt
) {
    public static CourseSummaryResponseDTO from(CourseEntity course) {
        return new CourseSummaryResponseDTO(
                course.getCourseId(),
                course.getTitle(),
                course.isFavorited(),
                course.getFavoritedAt(),
                course.getContentVersion(),
                course.getStopCount(),
                course.getFirstEventTitle(),
                course.getFirstEventImageUrl(),
                course.getShareId() != null,
                course.getCreatedAt(),
                course.getUpdatedAt()
        );
    }
}
