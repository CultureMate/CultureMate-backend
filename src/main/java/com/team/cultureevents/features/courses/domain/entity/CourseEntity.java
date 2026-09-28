package com.team.cultureevents.features.courses.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "course",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_course_share_id", columnNames = "share_id")
        }
)
public class CourseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 50)
    private String title;

    @Column(nullable = false)
    private boolean favorited;

    @Column(name = "favorited_at")
    private Instant favoritedAt;

    @Column(name = "share_id", length = 64)
    private String shareId;

    @Column(name = "content_version", nullable = false)
    private long contentVersion;

    @Column(name = "stop_count", nullable = false)
    private int stopCount;

    @Column(name = "first_event_title", length = 300)
    private String firstEventTitle;

    @Column(name = "first_event_image_url", length = 1000)
    private String firstEventImageUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stopOrder ASC")
    private List<CourseStopEntity> stops = new ArrayList<>();

    protected CourseEntity() {
    }

    public CourseEntity(Long memberId, String title, Instant now) {
        this.memberId = memberId;
        this.title = title;
        this.favorited = false;
        this.contentVersion = 1L;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void initializeStops(List<CourseStopEntity> newStops) {
        replaceStops(newStops);
        refreshSummary();
    }

    public void updateContent(String title, List<CourseStopEntity> newStops, Instant now) {
        this.title = title;
        replaceStops(newStops);
        this.updatedAt = now;
        this.contentVersion++;
        refreshSummary();
    }

    public void setFavorited(boolean favorited, Instant now) {
        if (this.favorited == favorited) {
            return;
        }
        this.favorited = favorited;
        this.favoritedAt = favorited ? now : null;
    }

    public void enableShare(String shareId) {
        if (this.shareId == null) {
            this.shareId = shareId;
        }
    }

    public void disableShare() {
        this.shareId = null;
    }

    private void replaceStops(List<CourseStopEntity> newStops) {
        this.stops.clear();
        for (CourseStopEntity stop : newStops) {
            stop.attachTo(this);
            this.stops.add(stop);
        }
    }

    private void refreshSummary() {
        this.stopCount = stops.size();
        CourseStopEntity firstEvent = stops.stream()
                .filter(stop -> stop.getType() == CourseStopType.EVENT)
                .findFirst()
                .orElse(null);
        this.firstEventTitle = firstEvent == null ? null : firstEvent.getEventTitle();
        this.firstEventImageUrl = firstEvent == null ? null : firstEvent.getEventImageUrl();
    }

    public Long getCourseId() {
        return courseId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getTitle() {
        return title;
    }

    public boolean isFavorited() {
        return favorited;
    }

    public Instant getFavoritedAt() {
        return favoritedAt;
    }

    public String getShareId() {
        return shareId;
    }

    public long getContentVersion() {
        return contentVersion;
    }

    public int getStopCount() {
        return stopCount;
    }

    public String getFirstEventTitle() {
        return firstEventTitle;
    }

    public String getFirstEventImageUrl() {
        return firstEventImageUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<CourseStopEntity> getStops() {
        return stops;
    }
}
