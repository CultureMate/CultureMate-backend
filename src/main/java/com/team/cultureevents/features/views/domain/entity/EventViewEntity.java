package com.team.cultureevents.features.views.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 행사별 조회수. eventId(문화포털 URL 등)를 키로 둡니다. */
@Entity
@Table(name = "event_view")
public class EventViewEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 512)
    private String eventId;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Version
    private Long version;

    protected EventViewEntity() {
    }

    public EventViewEntity(String eventId, int viewCount) {
        this.eventId = eventId;
        this.viewCount = viewCount;
    }

    public String getEventId() {
        return eventId;
    }

    public int getViewCount() {
        return viewCount;
    }

    public int increment() {
        this.viewCount += 1;
        return this.viewCount;
    }
}
