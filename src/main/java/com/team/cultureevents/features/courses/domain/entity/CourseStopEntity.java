package com.team.cultureevents.features.courses.domain.entity;

import com.team.cultureevents.features.commons.util.EventDates;
import com.team.cultureevents.features.events.domain.dto.EventDetailResponseDTO;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "course_stop")
public class CourseStopEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_stop_id")
    private Long courseStopId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private CourseEntity course;

    @Column(name = "stop_order", nullable = false)
    private int stopOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_type", nullable = false, length = 20)
    private CourseStopType type;

    @Column(name = "event_id", length = 512)
    private String eventId;

    @Column(name = "place_id", length = 255)
    private String placeId;

    @Column(name = "event_title", length = 300)
    private String eventTitle;

    @Column(name = "event_category", length = 100)
    private String eventCategory;

    @Column(name = "event_district", length = 100)
    private String eventDistrict;

    @Column(name = "event_place", length = 300)
    private String eventPlace;

    @Column(name = "event_start_date")
    private LocalDate eventStartDate;

    @Column(name = "event_end_date")
    private LocalDate eventEndDate;

    @Column(name = "event_image_url", length = 1000)
    private String eventImageUrl;

    @Column(name = "event_latitude")
    private Double eventLatitude;

    @Column(name = "event_longitude")
    private Double eventLongitude;

    protected CourseStopEntity() {
    }

    private CourseStopEntity(int stopOrder, CourseStopType type) {
        this.stopOrder = stopOrder;
        this.type = type;
    }

    public static CourseStopEntity event(int stopOrder, EventDetailResponseDTO detail) {
        CourseStopEntity stop = new CourseStopEntity(stopOrder, CourseStopType.EVENT);
        stop.eventId = detail.eventId();
        stop.eventTitle = clip(blank(detail.title()), 300);
        stop.eventCategory = clip(blank(detail.category()), 100);
        stop.eventDistrict = clip(blank(detail.district()), 100);
        stop.eventPlace = clip(blank(detail.place()), 300);
        stop.eventStartDate = EventDates.parseFlexible(detail.startDate());
        stop.eventEndDate = EventDates.parseFlexible(detail.endDate());
        stop.eventImageUrl = clip(blank(detail.imageUrl()), 1000);
        stop.eventLatitude = detail.latitude();
        stop.eventLongitude = detail.longitude();
        return stop;
    }

    public static CourseStopEntity eventFromSnapshot(int stopOrder, CourseStopEntity source) {
        CourseStopEntity stop = new CourseStopEntity(stopOrder, CourseStopType.EVENT);
        stop.eventId = source.eventId;
        stop.eventTitle = source.eventTitle;
        stop.eventCategory = source.eventCategory;
        stop.eventDistrict = source.eventDistrict;
        stop.eventPlace = source.eventPlace;
        stop.eventStartDate = source.eventStartDate;
        stop.eventEndDate = source.eventEndDate;
        stop.eventImageUrl = source.eventImageUrl;
        stop.eventLatitude = source.eventLatitude;
        stop.eventLongitude = source.eventLongitude;
        return stop;
    }

    public static CourseStopEntity place(int stopOrder, CourseStopType type, String placeId) {
        CourseStopEntity stop = new CourseStopEntity(stopOrder, type);
        stop.placeId = placeId;
        return stop;
    }

    void attachTo(CourseEntity course) {
        this.course = course;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public Long getCourseStopId() {
        return courseStopId;
    }

    public int getStopOrder() {
        return stopOrder;
    }

    public CourseStopType getType() {
        return type;
    }

    public String getEventId() {
        return eventId;
    }

    public String getPlaceId() {
        return placeId;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public String getEventCategory() {
        return eventCategory;
    }

    public String getEventDistrict() {
        return eventDistrict;
    }

    public String getEventPlace() {
        return eventPlace;
    }

    public LocalDate getEventStartDate() {
        return eventStartDate;
    }

    public LocalDate getEventEndDate() {
        return eventEndDate;
    }

    public String getEventImageUrl() {
        return eventImageUrl;
    }

    public Double getEventLatitude() {
        return eventLatitude;
    }

    public Double getEventLongitude() {
        return eventLongitude;
    }
}
