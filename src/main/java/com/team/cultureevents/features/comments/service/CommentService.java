package com.team.cultureevents.features.comments.service;

import com.team.cultureevents.features.comments.domain.dto.CommentResponseDTO;
import com.team.cultureevents.features.comments.domain.entity.CommentEntity;
import com.team.cultureevents.features.comments.repository.CommentRepository;
import com.team.cultureevents.features.commons.handler.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class CommentService {

    private final CommentRepository commentRepository;

    public CommentService(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    @Transactional
    public CommentResponseDTO create(String eventId, Long memberId, Long parentId, String content) {
        if (parentId != null) {
            CommentEntity parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> BusinessException.notFound("부모 댓글을 찾을 수 없습니다."));
            if (!parent.getEventId().equals(eventId)) {
                throw BusinessException.badRequest("부모 댓글과 행사가 일치하지 않습니다.");
            }
        }
        CommentEntity comment = new CommentEntity(eventId, memberId, parentId, content.trim(), Instant.now());
        return CommentResponseDTO.from(commentRepository.save(comment));
    }

    @Transactional(readOnly = true)
    public List<CommentResponseDTO> list(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw BusinessException.badRequest("eventId는 필수입니다.");
        }
        return commentRepository.findByEventIdOrderByCreatedAtAsc(eventId).stream()
                .map(CommentResponseDTO::from)
                .toList();
    }

    @Transactional
    public CommentResponseDTO update(Long commentId, Long memberId, String content) {
        CommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> BusinessException.notFound("댓글을 찾을 수 없습니다."));
        requireOwner(comment, memberId);
        comment.updateContent(content.trim(), Instant.now());
        return CommentResponseDTO.from(comment);
    }

    @Transactional
    public void delete(Long commentId, Long memberId) {
        CommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> BusinessException.notFound("댓글을 찾을 수 없습니다."));
        requireOwner(comment, memberId);
        commentRepository.delete(comment);
    }

    private static void requireOwner(CommentEntity comment, Long memberId) {
        if (!comment.getMemberId().equals(memberId)) {
            throw new BusinessException("FORBIDDEN", "본인 댓글만 수정·삭제할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
    }
}
