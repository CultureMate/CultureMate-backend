package com.team.cultureevents.features.comments.service;

import com.team.cultureevents.features.comments.domain.entity.CommentEntity;
import com.team.cultureevents.features.comments.repository.CommentRepository;
import com.team.cultureevents.features.commons.handler.BusinessException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class CommentService {

    private final CommentRepository commentRepository;

    public CommentService(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    public CommentEntity createComment(String eventId, Long memberId, Long parentId, String content) {
        if (content == null || content.isBlank()) {
            throw BusinessException.badRequest("댓글 내용을 입력해주세요.");
        }
        CommentEntity comment = new CommentEntity(eventId, memberId, parentId, content, Instant.now());
        return commentRepository.save(comment);
    }

    public List<CommentEntity> getComments(String eventId) {
        return commentRepository.findByEventIdOrderByCreatedAtAsc(eventId);
    }

    public CommentEntity updateComment(Long commentId, Long memberId, String content) {
        CommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> BusinessException.notFound("댓글을 찾을 수 없습니다."));

        if (!comment.getMemberId().equals(memberId)) {
            throw BusinessException.badRequest("본인 댓글만 수정할 수 있습니다.");
        }

        comment.updateContent(content, Instant.now());
        return comment;
    }

    public void deleteComment(Long commentId, Long memberId) {
        CommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> BusinessException.notFound("댓글을 찾을 수 없습니다."));

        if (!comment.getMemberId().equals(memberId)) {
            throw BusinessException.badRequest("본인 댓글만 삭제할 수 있습니다.");
        }

        commentRepository.deleteById(commentId);
    }
}