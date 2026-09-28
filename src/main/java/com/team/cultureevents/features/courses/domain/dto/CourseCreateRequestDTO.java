package com.team.cultureevents.features.courses.domain.dto;

import java.util.List;

public record CourseCreateRequestDTO(
        String title,
        List<CourseStopRequestDTO> stops
) {
}
