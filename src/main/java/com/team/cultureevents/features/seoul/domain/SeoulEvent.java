package com.team.cultureevents.features.seoul.domain;

import java.util.List;

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
        Double longitude,
        List<String> aliasIds
) {
    /** aliasIds: 중복 등록으로 합쳐진 다른 행의 eventId. 이 ID로 상세 조회가 들어와도 이 행사로 연결한다. */
    public SeoulEvent {
        aliasIds = aliasIds == null ? List.of() : List.copyOf(aliasIds);
    }

    /** 중복 별칭이 없는 행사용. */
    public SeoulEvent(String eventId, String title, String category, String district, String place,
                      String startDate, String endDate, String fee, String organization,
                      String originalUrl, String imageUrl, Double latitude, Double longitude) {
        this(eventId, title, category, district, place, startDate, endDate, fee, organization,
                originalUrl, imageUrl, latitude, longitude, List.of());
    }

    /** 좌표가 없는 행사(테스트·Mock 등)용. */
    public SeoulEvent(String eventId, String title, String category, String district, String place,
                      String startDate, String endDate, String fee, String organization,
                      String originalUrl, String imageUrl) {
        this(eventId, title, category, district, place, startDate, endDate, fee, organization,
                originalUrl, imageUrl, null, null);
    }
}
