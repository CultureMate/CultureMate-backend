package com.team.cultureevents.features.places.domain.dto;

public record PlaceCandidateDTO(
        String placeId,
        String name,
        String address,
        Double rating,
        Integer userRatingCount,
        Double latitude,
        Double longitude,
        String mapUrl
) {
}
