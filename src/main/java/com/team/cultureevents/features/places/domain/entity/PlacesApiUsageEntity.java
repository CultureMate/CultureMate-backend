package com.team.cultureevents.features.places.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * 날짜별 Google Places 호출 횟수. 하루에 한 행(row)만 존재한다.
 * 검색(callCount)과 사진(photoCallCount)은 서로 다른 과금 SKU라 따로 센다.
 */
@Entity
@Table(name = "places_api_usage")
public class PlacesApiUsageEntity {

    @Id
    @Column(name = "call_date")
    private LocalDate callDate;

    @Column(name = "call_count", nullable = false)
    private int callCount;

    // 이미 places_api_usage 테이블에 행이 있는 상태에서 이 컬럼이 새로 추가돼도(ddl-auto: update)
    // 시작 시 오류가 나지 않도록 기본값 0을 명시한다.
    @Column(name = "photo_call_count", nullable = false, columnDefinition = "integer default 0")
    private int photoCallCount;

    protected PlacesApiUsageEntity() {
        // JPA 전용
    }

    public PlacesApiUsageEntity(LocalDate callDate, int callCount, int photoCallCount) {
        this.callDate = callDate;
        this.callCount = callCount;
        this.photoCallCount = photoCallCount;
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

    public void incrementCallCount() {
        this.callCount++;
    }

    public void incrementPhotoCallCount() {
        this.photoCallCount++;
    }
}
