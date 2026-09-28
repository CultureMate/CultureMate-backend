package com.team.cultureevents.features.courses.domain.dto;

import com.team.cultureevents.features.courses.domain.entity.CourseEntity;

import java.time.Instant;
import java.util.List;

public record CourseDetailResponseDTO(
        Long courseId,
        String title,
        boolean favorited,
        Instant favoritedAt,
        String shareId,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<CourseStopResponseDTO> stops
) {
    public static CourseDetailResponseDTO from(CourseEntity course) {
        return new CourseDetailResponseDTO(
                course.getCourseId(),
                course.getTitle(),
                course.isFavorited(),
                course.getFavoritedAt(),
                course.getShareId(),
                course.getContentVersion(),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                course.getStops().stream().map(CourseStopResponseDTO::from).toList()
        );
    }
}
