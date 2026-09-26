package com.team.cultureevents.features.seoul.domain;

/**
 * 서울시 culturalEventInfo 정규화 모델 (캐시·필터용).
 * 원본 필드 매핑은 SeoulOpenApiClient에서 수행.
 */
public record SeoulEvent(
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
        Double latitude,
        Double longitude
) {
    /** 좌표가 없는 행사(테스트·Mock 등)용. */
    public SeoulEvent(String eventId, String title, String category, String district, String place,
                      String startDate, String endDate, String fee, String organization,
                      String originalUrl, String imageUrl) {
        this(eventId, title, category, district, place, startDate, endDate, fee, organization,
                originalUrl, imageUrl, null, null);
    }
}
