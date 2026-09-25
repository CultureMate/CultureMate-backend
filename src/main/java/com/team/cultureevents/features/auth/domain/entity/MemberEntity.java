package com.team.cultureevents.features.auth.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "member", uniqueConstraints = @UniqueConstraint(name = "uk_member_kakao", columnNames = "kakao_id"))
public class MemberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long memberId;

    @Column(name = "kakao_id", nullable = false, length = 64)
    private String kakaoId;

    @Column(length = 50)
    private String nickname;

    @Column(length = 50)
    private String residence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MemberEntity() {}

    public MemberEntity(String kakaoId, String nickname) {
        this.kakaoId = kakaoId;
        this.nickname = nickname;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getMemberId() { return memberId; }
    public String getKakaoId() { return kakaoId; }
    public String getNickname() { return nickname; }
    public String getResidence() { return residence; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateNickname(String nickname) {
        if (nickname != null && !nickname.isBlank() && !nickname.equals(this.nickname)) {
            this.nickname = nickname;
            this.updatedAt = Instant.now();
        }
    }
    public void updateResidence(String residence) {
    if (residence != null && !residence.isBlank() && !residence.equals(this.residence)) {
        this.residence = residence;
        this.updatedAt = Instant.now();
    }
}
}
