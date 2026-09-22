package com.team.cultureevents.features.comments.ctrl;

import com.team.cultureevents.features.auth.service.CurrentMemberService;
import com.team.cultureevents.features.comments.domain.dto.CommentCreateRequestDTO;
import com.team.cultureevents.features.comments.domain.dto.CommentResponseDTO;
import com.team.cultureevents.features.comments.domain.dto.CommentUpdateRequestDTO;
import com.team.cultureevents.features.comments.service.CommentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 행사 댓글. 작성·수정·삭제는 세션 쿠키 필요. 목록 조회는 공개. */
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;
    private final CurrentMemberService currentMember;

    public CommentController(CommentService commentService, CurrentMemberService currentMember) {
        this.commentService = commentService;
        this.currentMember = currentMember;
    }

    @PostMapping
    public ResponseEntity<CommentResponseDTO> create(
            @Valid @RequestBody CommentCreateRequestDTO body,
            HttpServletRequest request
    ) {
        Long memberId = currentMember.requireMember(request).getMemberId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.create(body.eventId(), memberId, body.parentId(), body.content()));
    }

    @GetMapping
    public ResponseEntity<List<CommentResponseDTO>> list(@RequestParam String eventId) {
        return ResponseEntity.ok(commentService.list(eventId));
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<CommentResponseDTO> update(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequestDTO body,
            HttpServletRequest request
    ) {
        Long memberId = currentMember.requireMember(request).getMemberId();
        return ResponseEntity.ok(commentService.update(commentId, memberId, body.content()));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long commentId,
            HttpServletRequest request
    ) {
        Long memberId = currentMember.requireMember(request).getMemberId();
        commentService.delete(commentId, memberId);
        return ResponseEntity.noContent().build();
    }
}
