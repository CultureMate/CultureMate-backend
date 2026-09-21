package com.team.cultureevents.features.auth.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "auth_session")
public class AuthSessionEntity {
    @Id
    @Column(name = "session_id", length = 36)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberEntity member;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuthSessionEntity() {}

    public AuthSessionEntity(String sessionId, MemberEntity member, Instant expiresAt) {
        this.sessionId = sessionId;
        this.member = member;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public String getSessionId() { return sessionId; }
    public MemberEntity getMember() { return member; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
