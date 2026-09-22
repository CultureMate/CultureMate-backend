package com.team.cultureevents.features.comments.domain.dto;

import com.team.cultureevents.features.comments.domain.entity.CommentEntity;

import java.time.Instant;

public record CommentResponseDTO(
        Long commentId,
        String eventId,
        Long memberId,
        Long parentId,
        String content,
        Instant createdAt,
        Instant updatedAt
) {
    public static CommentResponseDTO from(CommentEntity entity) {
        return new CommentResponseDTO(
                entity.getCommentId(),
                entity.getEventId(),
                entity.getMemberId(),
                entity.getParentId(),
                entity.getContent(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
