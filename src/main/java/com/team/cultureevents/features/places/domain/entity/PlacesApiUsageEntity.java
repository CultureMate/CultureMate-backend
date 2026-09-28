package com.team.cultureevents.features.places.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** 날짜별 Google Places 호출 횟수. 검색·사진·상세 조회 과금 항목을 각각 센다. */
@Entity
@Table(name = "places_api_usage")
public class PlacesApiUsageEntity {

    @Id
    @Column(name = "call_date")
    private LocalDate callDate;

    @Column(name = "call_count", nullable = false)
    private int callCount;

    @Column(name = "photo_call_count", nullable = false, columnDefinition = "integer default 0")
    private int photoCallCount;

    @Column(name = "detail_call_count", nullable = false, columnDefinition = "integer default 0")
    private int detailCallCount;

    protected PlacesApiUsageEntity() {
    }

    public PlacesApiUsageEntity(LocalDate callDate, int callCount, int photoCallCount) {
        this(callDate, callCount, photoCallCount, 0);
    }

    public PlacesApiUsageEntity(LocalDate callDate, int callCount, int photoCallCount, int detailCallCount) {
        this.callDate = callDate;
        this.callCount = callCount;
        this.photoCallCount = photoCallCount;
        this.detailCallCount = detailCallCount;
    }

    public LocalDate getCallDate() {
        return callDate;
    }

    public int getCallCount() {
        return callCount;
    }

    public int getPhotoCallCount() {
        return photoCallCount;
    }

    public int getDetailCallCount() {
        return detailCallCount;
    }

    public void incrementCallCount() {
        this.callCount++;
    }

    public void incrementPhotoCallCount() {
        this.photoCallCount++;
    }

    public void incrementDetailCallCount() {
        this.detailCallCount++;
    }
}
