package com.team.cultureevents.features.favorites.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

/**
 * MVP: browser_key + event_id 유니크. member_id는 로그인 연동 이후.
 */
@Entity
@Table(
        name = "favorite",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_favorite_browser_event", columnNames = {"browser_key", "event_id"}),
                @UniqueConstraint(name = "uk_favorite_member_event", columnNames = {"member_id", "event_id"})
        }
)
public class FavoriteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long favoriteId;

    @Column(name = "browser_key", length = 64)
    private String browserKey;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "event_id", nullable = false, length = 512)
    private String eventId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(length = 300)
    private String place;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    protected FavoriteEntity() {
    }

    public FavoriteEntity(
            String browserKey,
            String eventId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            String place,
            Instant savedAt
    ) {
        this.browserKey = browserKey;
        this.eventId = eventId;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.place = place;
        this.savedAt = savedAt;
    }

    public Long getFavoriteId() {
        return favoriteId;
    }

    public String getBrowserKey() {
        return browserKey;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getPlace() {
        return place;
    }

    public Instant getSavedAt() {
        return savedAt;
    }
}
