package com.team.cultureevents.features.main.domain.dto;

/** 홈 HOT/근처 카드용. FE Home.jsx가 쓰는 필드를 넉넉히 담는다. */
public record HomeEventItemDTO(
        String eventId,
        String title,
        String category,
        String district,
        String place,
        String startDate,
        String endDate,
        String imageUrl,
        Integer viewCount,
        Integer dDay
) {
}
