package com.team.cultureevents.features.summary.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 행사 AI 소개문 (POST /api/events/{eventId}/summary). */
@Entity
@Table(name = "ai_summary")
public class AiSummaryEntity {

    @Id
    @Column(name = "event_id", length = 512)
    private String eventId;

    // @Lob만 두면 MariaDB에서 tinytext(255바이트)로 잡혀 긴 소개문이 저장되지 않는다.
    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiSummaryEntity() {
    }

    public AiSummaryEntity(String eventId, String summary, Instant createdAt) {
        this.eventId = eventId;
        this.summary = summary;
        this.createdAt = createdAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
