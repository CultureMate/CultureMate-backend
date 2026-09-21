package com.culturemate.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventName;
    private String category;
    private String district;      // 자치구
    private String location;
    private String period;
    private String fee;
    private String organizer;
    private String originalUrl;

    @Column(length = 1000)
    private String aiSummary;     // GPT 요약 (강민구님 담당)

    private int viewCount = 0;
}