package com.team.cultureevents.features.favorites.domain.dto;

import com.team.cultureevents.features.favorites.domain.entity.FavoriteEntity;

import java.time.Instant;
import java.time.LocalDate;

/** 관심 행사 응답 (저장 스냅샷) */
public record FavoriteResponseDTO(
        String eventId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        String place,
        Instant savedAt
) {
    public static FavoriteResponseDTO fromEntity(FavoriteEntity entity) {
        return new FavoriteResponseDTO(
                entity.getEventId(),
                entity.getTitle(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getPlace(),
                entity.getSavedAt()
        );
    }
}
