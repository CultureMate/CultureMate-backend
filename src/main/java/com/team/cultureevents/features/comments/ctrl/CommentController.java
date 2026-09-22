package com.team.cultureevents.features.comments.ctrl;

import com.team.cultureevents.features.comments.domain.entity.CommentEntity;
import com.team.cultureevents.features.comments.service.CommentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public ResponseEntity<CommentEntity> createComment(
            @RequestParam String eventId,
            @RequestParam Long memberId,
            @RequestParam(required = false) Long parentId,
            @RequestParam String content) {
        CommentEntity comment = commentService.createComment(eventId, memberId, parentId, content);
        return ResponseEntity.ok(comment);
    }

    @GetMapping
    public List<CommentEntity> getComments(@RequestParam String eventId) {
        return commentService.getComments(eventId);
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<CommentEntity> updateComment(
            @PathVariable Long commentId,
            @RequestParam Long memberId,
            @RequestParam String content) {
        CommentEntity comment = commentService.updateComment(commentId, memberId, content);
        return ResponseEntity.ok(comment);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long commentId,
            @RequestParam Long memberId) {
        commentService.deleteComment(commentId, memberId);
        return ResponseEntity.noContent().build();
    }
}