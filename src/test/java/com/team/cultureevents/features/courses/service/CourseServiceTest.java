package com.team.cultureevents.features.courses.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.courses.domain.dto.CourseCreateRequestDTO;
import com.team.cultureevents.features.courses.domain.dto.CourseFavoriteRequestDTO;
import com.team.cultureevents.features.courses.domain.dto.CourseStopRequestDTO;
import com.team.cultureevents.features.courses.domain.dto.CourseUpdateRequestDTO;
import com.team.cultureevents.features.courses.domain.entity.CourseEntity;
import com.team.cultureevents.features.courses.domain.entity.CourseStopEntity;
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
    private final CourseService service = new CourseService(courses, events);

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
        verify(courses, never()).save(any());
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
