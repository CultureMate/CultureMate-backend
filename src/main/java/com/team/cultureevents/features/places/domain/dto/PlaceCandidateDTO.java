package com.team.cultureevents.features.places.domain.dto;

public record PlaceCandidateDTO(
        String placeId,
        String name,
        String address,
        Double rating,
        Integer userRatingCount,
        Double latitude,
        Double longitude,
        String mapUrl,
        String photoName,
        java.util.List<AuthorAttribution> authorAttributions,
        String businessStatus,
        Boolean openNow,
        Double detourMeters,
        Double recommendationScore
) {
    public record AuthorAttribution(String displayName, String uri, String photoUri) {}

    public PlaceCandidateDTO {
        authorAttributions = authorAttributions == null ? java.util.List.of() : java.util.List.copyOf(authorAttributions);
    }

    public PlaceCandidateDTO(String placeId, String name, String address, Double rating,
            Integer userRatingCount, Double latitude, Double longitude, String mapUrl,
            String photoName, String photoAttribution, String businessStatus, Boolean openNow) {
        this(placeId, name, address, rating, userRatingCount, latitude, longitude, mapUrl,
                photoName, photoAttribution == null ? java.util.List.of() : java.util.List.of(
                        new AuthorAttribution(photoAttribution, null, null)), businessStatus, openNow, null, null);
    }

    public PlaceCandidateDTO ranked(double detour, double score) {
        return new PlaceCandidateDTO(placeId, name, address, rating, userRatingCount, latitude,
                longitude, mapUrl, photoName, authorAttributions, businessStatus, openNow, detour, score);
    }
}
