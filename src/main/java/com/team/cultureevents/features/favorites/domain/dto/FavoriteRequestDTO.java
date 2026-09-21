package com.team.cultureevents.features.favorites.domain.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/favorites body */
public record FavoriteRequestDTO(
        @NotBlank(message = "eventId는 필수입니다.")
        String eventId
) {
}
