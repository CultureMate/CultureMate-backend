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
        String imageUrl
) {
}
