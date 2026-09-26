package com.team.cultureevents.features.comments.service;

import com.team.cultureevents.features.auth.domain.entity.MemberEntity;
import com.team.cultureevents.features.auth.repository.MemberRepository;
import com.team.cultureevents.features.comments.domain.dto.CommentResponseDTO;
import com.team.cultureevents.features.comments.domain.entity.CommentEntity;
import com.team.cultureevents.features.comments.repository.CommentRepository;
import com.team.cultureevents.features.commons.handler.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final MemberRepository memberRepository;

    public CommentService(CommentRepository commentRepository, MemberRepository memberRepository) {
        this.commentRepository = commentRepository;
        this.memberRepository = memberRepository;
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
        return withNickname(commentRepository.save(comment));
    }

    @Transactional(readOnly = true)
    public List<CommentResponseDTO> list(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw BusinessException.badRequest("eventId는 필수입니다.");
        }
        List<CommentEntity> comments = commentRepository.findByEventIdOrderByCreatedAtAsc(eventId);
        Map<Long, String> nicknames = memberRepository.findAllById(
                        comments.stream().map(CommentEntity::getMemberId).distinct().toList()).stream()
                .filter(member -> member.getNickname() != null)
                .collect(Collectors.toMap(MemberEntity::getMemberId, MemberEntity::getNickname));
        return comments.stream()
                .map(comment -> CommentResponseDTO.from(comment, nicknames.get(comment.getMemberId())))
                .toList();
    }

    @Transactional
    public CommentResponseDTO update(Long commentId, Long memberId, String content) {
        CommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> BusinessException.notFound("댓글을 찾을 수 없습니다."));
        requireOwner(comment, memberId);
        comment.updateContent(content.trim(), Instant.now());
        return withNickname(comment);
    }

    @Transactional
    public void delete(Long commentId, Long memberId) {
        CommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> BusinessException.notFound("댓글을 찾을 수 없습니다."));
        requireOwner(comment, memberId);
        // 대댓글이 남아 고아가 되지 않도록 상위 댓글과 함께 삭제한다.
        commentRepository.deleteAll(commentRepository.findByParentId(commentId));
        commentRepository.delete(comment);
    }

    private CommentResponseDTO withNickname(CommentEntity comment) {
        String nickname = memberRepository.findById(comment.getMemberId())
                .map(MemberEntity::getNickname)
                .orElse(null);
        return CommentResponseDTO.from(comment, nickname);
    }

    private static void requireOwner(CommentEntity comment, Long memberId) {
        if (!comment.getMemberId().equals(memberId)) {
            throw new BusinessException("FORBIDDEN", "본인 댓글만 수정·삭제할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
    }
}
