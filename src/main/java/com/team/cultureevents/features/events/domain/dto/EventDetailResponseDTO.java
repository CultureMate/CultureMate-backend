package com.team.cultureevents.features.events.domain.dto;

public record EventDetailResponseDTO(
        String eventId,
        String title,
        String category,
        String district,
        String place,
        String startDate,
        String endDate,
        String fee,
        String organization,
        String originalUrl,
        String imageUrl,
        Integer viewCount
) {
}
