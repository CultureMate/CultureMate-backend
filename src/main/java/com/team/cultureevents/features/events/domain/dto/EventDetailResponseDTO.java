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
        Integer viewCount,
        Double latitude,
        Double longitude
) {
    /** 좌표 없이 생성하는 경우(테스트 등). */
    public EventDetailResponseDTO(String eventId, String title, String category, String district, String place,
                                  String startDate, String endDate, String fee, String organization,
                                  String originalUrl, String imageUrl, Integer viewCount) {
        this(eventId, title, category, district, place, startDate, endDate, fee, organization,
                originalUrl, imageUrl, viewCount, null, null);
    }
}
