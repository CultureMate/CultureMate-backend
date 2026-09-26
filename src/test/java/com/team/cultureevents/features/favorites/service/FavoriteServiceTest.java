package com.team.cultureevents.features.favorites.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.events.domain.dto.EventDetailResponseDTO;
import com.team.cultureevents.features.events.service.EventService;
import com.team.cultureevents.features.favorites.domain.dto.FavoriteRequestDTO;
import com.team.cultureevents.features.favorites.domain.entity.FavoriteEntity;
import com.team.cultureevents.features.favorites.repository.FavoriteRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FavoriteServiceTest {

    private final FavoriteRepository repository = mock(FavoriteRepository.class);
    private final EventService events = mock(EventService.class);
    private final FavoriteService service = new FavoriteService(repository, events);

    @Test
    void saveStoresEventSnapshotAndRejectsDuplicate() {
        EventDetailResponseDTO detail = new EventDetailResponseDTO(
                "https://culture.seoul.go.kr/event?code=A+B",
                "마포 가을 전시", "전시/미술", "마포구", "문화회관",
                "2026-09-20", "2026-09-25", "무료", "서울시", "https://example.com", "", null);
        when(repository.findByMemberIdAndEventId(1L, detail.eventId())).thenReturn(Optional.empty());
        when(events.getDetail(detail.eventId())).thenReturn(detail);
        when(repository.save(any(FavoriteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var saved = service.save(1L, new FavoriteRequestDTO(detail.eventId()));

        assertEquals(detail.eventId(), saved.eventId());
        assertEquals("마포 가을 전시", saved.title());
        assertEquals(LocalDate.of(2026, 9, 20), saved.startDate());
        assertEquals("문화회관", saved.place());
        ArgumentCaptor<FavoriteEntity> captor = ArgumentCaptor.forClass(FavoriteEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(1L, captor.getValue().getMemberId());

        when(repository.findByMemberIdAndEventId(1L, detail.eventId()))
                .thenReturn(Optional.of(captor.getValue()));
        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> service.save(1L, new FavoriteRequestDTO(detail.eventId())));
        assertEquals("ALREADY_SAVED", duplicate.getCode());
        verify(repository).save(any(FavoriteEntity.class));
    }

    @Test
    void listFiltersByMonthAndDeleteRequiresExistingRow() {
        FavoriteEntity september = new FavoriteEntity(1L, "event-1", "가을 전시",
                LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 25), "문화회관", Instant.parse("2026-09-21T00:00:00Z"));
        FavoriteEntity october = new FavoriteEntity(1L, "event-2", "10월 공연",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), "공연장", Instant.parse("2026-09-21T01:00:00Z"));
        when(repository.findByMemberIdOrderBySavedAtDesc(1L)).thenReturn(List.of(october, september));

        assertEquals(1, service.list(1L, "2026-09").size());
        assertEquals("event-1", service.list(1L, "2026-09").get(0).eventId());
        assertEquals(2, service.list(1L, null).size());
        assertEquals("INVALID_PARAM", assertThrows(BusinessException.class,
                () -> service.list(1L, "2026/09")).getCode());

        when(repository.findByMemberIdAndEventId(1L, "missing")).thenReturn(Optional.empty());
        assertEquals("NOT_FOUND", assertThrows(BusinessException.class,
                () -> service.delete(1L, "missing")).getCode());
        verify(repository, never()).delete(any());

        when(repository.findByMemberIdAndEventId(1L, "event-1")).thenReturn(Optional.of(september));
        service.delete(1L, "event-1");
        verify(repository).delete(september);
    }

    @Test
    void favoritesAreSeparatedByMember() {
        FavoriteEntity ofMemberA = new FavoriteEntity(1L, "event-1", "가을 전시",
                LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 25), "문화회관", Instant.parse("2026-09-21T00:00:00Z"));
        when(repository.findByMemberIdOrderBySavedAtDesc(1L)).thenReturn(List.of(ofMemberA));
        when(repository.findByMemberIdOrderBySavedAtDesc(2L)).thenReturn(List.of());
        when(repository.findByMemberIdAndEventId(2L, "event-1")).thenReturn(Optional.empty());

        assertEquals(1, service.list(1L, null).size());
        assertEquals(0, service.list(2L, null).size());
        assertEquals("NOT_FOUND", assertThrows(BusinessException.class,
                () -> service.delete(2L, "event-1")).getCode());
        verify(repository, never()).delete(any());
    }
}
