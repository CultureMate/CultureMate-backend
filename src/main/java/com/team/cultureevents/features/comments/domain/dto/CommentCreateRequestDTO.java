package com.team.cultureevents.features.comments.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentCreateRequestDTO(
        @NotBlank(message = "eventId는 필수입니다.")
        String eventId,
        @NotBlank(message = "댓글 내용을 입력해주세요.")
        @Size(max = 1000, message = "댓글은 1000자 이하여야 합니다.")
        String content,
        Long parentId
) {
}
