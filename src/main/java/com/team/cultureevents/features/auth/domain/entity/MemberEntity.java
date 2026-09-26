package com.team.cultureevents.features.auth.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

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

    /** 관심 카테고리. 쉼표로 이어 붙여 저장한다(예: "전시,공연"). */
    @Column(name = "interest_categories", length = 200)
    private String interestCategories;

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
    public List<String> getInterestCategories() {
        if (interestCategories == null || interestCategories.isBlank()) return List.of();
        return Arrays.stream(interestCategories.split(",")).map(String::trim).filter(v -> !v.isBlank()).toList();
    }
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
    public void updateInterestCategories(List<String> categories) {
        if (categories == null) return;
        String joined = String.join(",", categories);
        if (!joined.equals(this.interestCategories == null ? "" : this.interestCategories)) {
            this.interestCategories = joined;
            this.updatedAt = Instant.now();
        }
    }
}
