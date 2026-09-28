package com.team.cultureevents.features.places.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "places_quota_bucket")
public class PlacesQuotaBucket {
    @Id
    private String id;
    @Version
    private Long version;
    private long windowStart;
    private int usageCount;
    protected PlacesQuotaBucket() {}
    public PlacesQuotaBucket(String id) { this.id = id; }
    public int count(long window) { return windowStart == window ? usageCount : 0; }
    public void increment(long window) {
        usageCount = count(window) + 1;
        windowStart = window;
    }
}
