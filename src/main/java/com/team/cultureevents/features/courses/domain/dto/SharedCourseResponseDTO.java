package com.team.cultureevents.features.courses.domain.dto;

import com.team.cultureevents.features.courses.domain.entity.CourseEntity;

import java.time.Instant;
import java.util.List;

public record SharedCourseResponseDTO(
        String title,
        Instant createdAt,
        Instant updatedAt,
        List<CourseStopResponseDTO> stops
) {
    public static SharedCourseResponseDTO from(CourseEntity course) {
        return new SharedCourseResponseDTO(
                course.getTitle(),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                course.getStops().stream().map(CourseStopResponseDTO::from).toList()
        );
    }
}
