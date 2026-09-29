package com.team.cultureevents.features.courses.service;

import com.team.cultureevents.features.auth.domain.entity.MemberEntity;
import com.team.cultureevents.features.auth.repository.MemberRepository;
import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.courses.domain.dto.CourseCreateRequestDTO;
import com.team.cultureevents.features.courses.domain.dto.CourseFavoriteRequestDTO;
import com.team.cultureevents.features.courses.domain.dto.CourseStopRequestDTO;
import com.team.cultureevents.features.courses.domain.dto.CourseUpdateRequestDTO;
import com.team.cultureevents.features.courses.domain.entity.CourseEntity;
import com.team.cultureevents.features.courses.domain.entity.CourseStopEntity;
import com.team.cultureevents.features.courses.domain.entity.CourseStopType;
import com.team.cultureevents.features.courses.repository.CourseRepository;
import com.team.cultureevents.features.events.domain.dto.EventDetailResponseDTO;
import com.team.cultureevents.features.events.service.EventService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseServiceTest {

    private final CourseRepository courses = mock(CourseRepository.class);
    private final EventService events = mock(EventService.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final CourseService service = new CourseService(courses, events, members);

    CourseServiceTest() {
        when(members.findByIdForUpdate(7L)).thenReturn(Optional.of(new MemberEntity("kakao-7", "회원")));
    }

    @Test
    void createStoresEventSnapshotAndOnlyPlaceIdForPlaceStop() {
        when(events.getDetail("raw-event")).thenReturn(event("canonical-event", "행사 A"));
        when(courses.save(any(CourseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(7L, new CourseCreateRequestDTO(
                "  성수 산책  ",
                List.of(
                        new CourseStopRequestDTO("event", "raw-event", null),
                        new CourseStopRequestDTO("cafe", null, "ChIJ_cafe-1")
                )
        ));

        assertThat(result.title()).isEqualTo("성수 산책");
        assertThat(result.version()).isEqualTo(1L);
        assertThat(result.stops()).hasSize(2);
        assertThat(result.stops().get(0).eventId()).isEqualTo("canonical-event");
        assertThat(result.stops().get(0).eventTitle()).isEqualTo("행사 A");
        assertThat(result.stops().get(1).placeId()).isEqualTo("ChIJ_cafe-1");
        assertThat(result.stops().get(1).eventTitle()).isNull();
    }

    @Test
    void createRequiresAtLeastOneEventAndRejectsDuplicates() {
        assertThatThrownBy(() -> service.create(7L, new CourseCreateRequestDTO(
                "카페만", List.of(new CourseStopRequestDTO("cafe", null, "ChIJ1")))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PARAM");

        when(events.getDetail("a")).thenReturn(event("same", "행사"));
        when(events.getDetail("b")).thenReturn(event("same", "행사"));
        assertThatThrownBy(() -> service.create(7L, new CourseCreateRequestDTO(
                "중복", List.of(
                        new CourseStopRequestDTO("event", "a", null),
                        new CourseStopRequestDTO("event", "b", null)))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PARAM");
    }

    @Test
    void createRejectsWhenMemberAlreadyHas50Courses() {
        when(events.getDetail("event-1")).thenReturn(event("event-1", "행사"));
        when(courses.countByMemberId(7L)).thenReturn(50L);

        assertThatThrownBy(() -> service.create(7L, new CourseCreateRequestDTO(
                "51번째", List.of(new CourseStopRequestDTO("event", "event-1", null)))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "COURSE_LIMIT_EXCEEDED");
        verify(members).findByIdForUpdate(7L);
        verify(courses, never()).save(any());
    }

    @Test
    void listReturnsOrderedPreviewStopsWithoutResolvingPlaces() {
        CourseEntity course = new CourseEntity(7L, "서울 문화 산책",
                Instant.parse("2026-09-28T00:00:00Z"));
        course.initializeStops(List.of(
                CourseStopEntity.event(0, event("event-1", "행사 1")),
                CourseStopEntity.place(1, CourseStopType.CAFE, "ChIJ_cafe"),
                CourseStopEntity.place(2, CourseStopType.RESTAURANT, "ChIJ_restaurant"),
                CourseStopEntity.event(3, event("event-2", "행사 2"))
        ));
        when(courses.findByMemberIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(course));

        var result = service.list(7L, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).previewStops()).hasSize(4);
        assertThat(result.get(0).previewStops())
                .extracting(stop -> stop.type())
                .containsExactly("event", "cafe", "restaurant", "event");
        assertThat(result.get(0).previewStops().get(0).name()).isEqualTo("행사 1");
        assertThat(result.get(0).previewStops().get(0).imageUrl())
                .isEqualTo("https://example.com/image.jpg");
        assertThat(result.get(0).previewStops().get(1).placeId()).isEqualTo("ChIJ_cafe");
        assertThat(result.get(0).previewStops().get(1).name()).isNull();
        assertThat(result.get(0).previewStops().get(1).imageUrl()).isNull();
        assertThat(result.get(0).previewStops().get(2).placeId()).isEqualTo("ChIJ_restaurant");
        assertThat(result.get(0).previewStops().get(2).name()).isNull();
        assertThat(result.get(0).previewStops().get(2).imageUrl()).isNull();
        verify(events, never()).getDetail(any());
    }

    @Test
    void listLimitsPreviewStopsToFour() {
        CourseEntity course = new CourseEntity(7L, "긴 코스",
                Instant.parse("2026-09-28T00:00:00Z"));
        course.initializeStops(List.of(
                CourseStopEntity.place(0, CourseStopType.RESTAURANT, "ChIJ_restaurant_1"),
                CourseStopEntity.event(1, event("event-1", "행사 1")),
                CourseStopEntity.place(2, CourseStopType.CAFE, "ChIJ_cafe_1"),
                CourseStopEntity.place(3, CourseStopType.RESTAURANT, "ChIJ_restaurant_2"),
                CourseStopEntity.event(4, event("event-2", "행사 2")),
                CourseStopEntity.place(5, CourseStopType.CAFE, "ChIJ_cafe_2"),
                CourseStopEntity.event(6, event("event-3", "행사 3"))
        ));
        when(courses.findByMemberIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(course));

        var result = service.list(7L, false);

        assertThat(result.get(0).stopCount()).isEqualTo(7);
        assertThat(result.get(0).previewStops())
                .extracting(stop -> stop.stopOrder())
                .containsExactly(0, 1, 2, 3);
        assertThat(result.get(0).previewStops())
                .extracting(stop -> stop.type())
                .containsExactly("restaurant", "event", "cafe", "restaurant");
    }

    @Test
    void updateReusesExistingEventSnapshotAndIncrementsContentVersion() {
        CourseEntity course = courseWithEvent(7L, "event-1", "저장 당시 제목");
        when(courses.findByIdForUpdate(10L)).thenReturn(Optional.of(course));
        when(courses.save(course)).thenReturn(course);

        var result = service.update(7L, 10L, new CourseUpdateRequestDTO(
                "수정된 코스",
                1L,
                List.of(
                        new CourseStopRequestDTO("event", "event-1", null),
                        new CourseStopRequestDTO("restaurant", null, "ChIJ_restaurant")
                )
        ));

        assertThat(result.version()).isEqualTo(2L);
        assertThat(result.stops().get(0).eventTitle()).isEqualTo("저장 당시 제목");
        verify(events, never()).getDetail(any());
    }

    @Test
    void updateRejectsStaleVersionBeforeChangingContent() {
        CourseEntity course = courseWithEvent(7L, "event-1", "행사");
        when(courses.findByIdForUpdate(10L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> service.update(7L, 10L, new CourseUpdateRequestDTO(
                "수정", 0L, List.of(new CourseStopRequestDTO("event", "event-1", null)))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "COURSE_VERSION_CONFLICT");
        assertThat(course.getContentVersion()).isEqualTo(1L);
    }

    @Test
    void favoriteDoesNotChangeContentVersion() {
        CourseEntity course = courseWithEvent(7L, "event-1", "행사");
        when(courses.findByIdForUpdate(10L)).thenReturn(Optional.of(course));
        when(courses.save(course)).thenReturn(course);

        var result = service.setFavorite(7L, 10L, new CourseFavoriteRequestDTO(true));

        assertThat(result.favorited()).isTrue();
        assertThat(result.version()).isEqualTo(1L);
    }

    @Test
    void otherMembersCourseIsForbidden() {
        CourseEntity course = courseWithEvent(8L, "event-1", "행사");
        when(courses.findWithStopsById(10L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> service.get(7L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    private static CourseEntity courseWithEvent(Long memberId, String eventId, String title) {
        CourseEntity course = new CourseEntity(memberId, "코스", Instant.parse("2026-09-28T00:00:00Z"));
        course.initializeStops(List.of(CourseStopEntity.event(0, event(eventId, title))));
        return course;
    }

    private static EventDetailResponseDTO event(String eventId, String title) {
        return new EventDetailResponseDTO(
                eventId, title, "전시", "성동구", "행사장",
                "2026-09-01", "2026-09-30", "무료", "서울시",
                "https://example.com/event", "https://example.com/image.jpg", 0,
                37.5, 127.0, null
        );
    }
}