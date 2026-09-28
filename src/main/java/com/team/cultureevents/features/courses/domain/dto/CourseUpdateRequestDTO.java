package com.team.cultureevents.features.courses.domain.dto;

import java.util.List;

public record CourseUpdateRequestDTO(
        String title,
        Long version,
        List<CourseStopRequestDTO> stops
) {
}
