package com.team.cultureevents.features.events.domain.dto;

import java.util.List;

public record EventListResponseDTO(
        int count,
        int totalCount,
        Integer page,
        Integer size,
        List<EventSummaryResponseDTO> events
) {
}
