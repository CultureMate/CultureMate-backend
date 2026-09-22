package com.team.cultureevents.features.comments.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "event_comment")
public class CommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long commentId;

    @Column(name = "event_id", nullable = false, length = 512)
    private String eventId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected CommentEntity() {
    }

    public CommentEntity(String eventId, Long memberId, Long parentId, String content, Instant createdAt) {
        this.eventId = eventId;
        this.memberId = memberId;
        this.parentId = parentId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getCommentId() {
        return commentId;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateContent(String content, Instant updatedAt) {
        this.content = content;
        this.updatedAt = updatedAt;
    }
}