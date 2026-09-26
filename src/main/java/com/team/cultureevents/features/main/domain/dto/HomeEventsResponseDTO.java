package com.team.cultureevents.features.main.domain.dto;

import java.util.List;

/** district: 다가오는 행사에 적용된 자치구(null이면 서울 전체). */
public record HomeEventsResponseDTO(List<HomeEventItemDTO> events, String district) {
    public HomeEventsResponseDTO(List<HomeEventItemDTO> events) {
        this(events, null);
    }
}
