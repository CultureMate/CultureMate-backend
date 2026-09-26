package com.team.cultureevents.features.comments.domain.dto;

import com.team.cultureevents.features.comments.domain.entity.CommentEntity;

import java.time.Instant;

public record CommentResponseDTO(
        Long commentId,
        String eventId,
        Long memberId,
        String nickname,
        Long parentId,
        String content,
        Instant createdAt,
        Instant updatedAt
) {
    /** nickname이 null이면 탈퇴 등으로 회원 정보가 없는 댓글이다. */
    public static CommentResponseDTO from(CommentEntity entity, String nickname) {
        return new CommentResponseDTO(
                entity.getCommentId(),
                entity.getEventId(),
                entity.getMemberId(),
                nickname,
                entity.getParentId(),
                entity.getContent(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
