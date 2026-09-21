package com.culturemate.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String browserId;      // 사용자 구분용 (로그인 없어서 임시)

    @Column(length = 500)
    private String eventId;        // = 문화포털상세URL (행사 식별키, 길어서 length 늘림)

    private String title;          // = 공연/행사명
    private String startDate;      // = 시작일
    private String endDate;        // = 종료일
    private String place;          // = 장소
}