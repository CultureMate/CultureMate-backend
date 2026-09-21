package com.team.cultureevents.features.summary.domain.dto;

import com.team.cultureevents.features.summary.domain.entity.AiSummaryEntity;

import java.time.Instant;

/** POST /api/events/.../summary 응답 */
public record SummaryResponseDTO(
        String eventId,
        String summary,
        Instant createdAt
) {
    public static SummaryResponseDTO fromEntity(AiSummaryEntity entity) {
        return new SummaryResponseDTO(
                entity.getEventId(),
                entity.getSummary(),
                entity.getCreatedAt()
        );
    }
}
