package com.team.cultureevents.features.places.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** 날짜별 Google Places 호출 횟수. 하루에 한 행(row)만 존재한다. */
@Entity
@Table(name = "places_api_usage")
public class PlacesApiUsageEntity {

    @Id
    @Column(name = "call_date")
    private LocalDate callDate;

    @Column(name = "call_count", nullable = false)
    private int callCount;

    protected PlacesApiUsageEntity() {
        // JPA 전용
    }

    public PlacesApiUsageEntity(LocalDate callDate, int callCount) {
        this.callDate = callDate;
        this.callCount = callCount;
    }

    public LocalDate getCallDate() {
        return callDate;
    }

    public int getCallCount() {
        return callCount;
    }

    public void incrementCallCount() {
        this.callCount++;
    }
}
