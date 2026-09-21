package com.team.cultureevents.features.events.domain.dto;

public record EventSummaryResponseDTO(
        String eventId,
        String title,
        String category,
        String district,
        String place,
        String startDate,
        String endDate,
        String imageUrl
) {
}
