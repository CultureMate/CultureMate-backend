package com.team.cultureevents.features.favorites.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.events.domain.dto.EventDetailResponseDTO;
import com.team.cultureevents.features.events.service.EventService;
import com.team.cultureevents.features.favorites.domain.dto.FavoriteRequestDTO;
import com.team.cultureevents.features.favorites.domain.entity.FavoriteEntity;
import com.team.cultureevents.features.favorites.repository.FavoriteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FavoriteServiceTest {

    private final FavoriteRepository repository = mock(FavoriteRepository.class);
    private final EventService events = mock(EventService.class);
    private final FavoriteService service = new FavoriteService(repository, events);

    @BeforeEach
    void eventIdsPassThrough() {
        lenient().when(events.canonicalEventId(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(events.eventIdsIncludingAliases(any()))
                .thenAnswer(invocation -> List.of(invocation.getArgument(0, String.class)));
    }

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
        verify(repository).deleteAll(List.of(september));
    }

    @Test
    void aliasFavoriteIsNotSavedAgainUnderCanonicalId() {
        when(events.canonicalEventId("canonical")).thenReturn("canonical");
        when(events.eventIdsIncludingAliases("canonical")).thenReturn(List.of("canonical", "alias"));
        when(repository.findByMemberIdAndEventId(1L, "canonical")).thenReturn(Optional.empty());
        when(repository.findByMemberIdAndEventId(1L, "alias"))
                .thenReturn(Optional.of(new FavoriteEntity(1L, "alias", "빅무브",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20), "DDP", Instant.now())));

        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> service.save(1L, new FavoriteRequestDTO("canonical")));

        assertEquals("ALREADY_SAVED", duplicate.getCode());
        verify(repository, never()).save(any());
    }

    @Test
    void listKeepsOneFavoritePerCanonicalIdAndDeleteRemovesAliasToo() {
        FavoriteEntity canonical = new FavoriteEntity(1L, "canonical", "빅무브",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20), "DDP", Instant.parse("2026-10-02T00:00:00Z"));
        FavoriteEntity alias = new FavoriteEntity(1L, "alias", "빅무브",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20), "DDP", Instant.parse("2026-10-01T00:00:00Z"));
        when(repository.findByMemberIdOrderBySavedAtDesc(1L)).thenReturn(List.of(canonical, alias));
        when(events.canonicalEventId("canonical")).thenReturn("canonical");
        when(events.canonicalEventId("alias")).thenReturn("canonical");

        var listed = service.list(1L, null);

        assertEquals(1, listed.size());
        assertEquals("canonical", listed.get(0).eventId());

        when(events.eventIdsIncludingAliases("canonical")).thenReturn(List.of("canonical", "alias"));
        when(repository.findByMemberIdAndEventId(1L, "canonical")).thenReturn(Optional.of(canonical));
        when(repository.findByMemberIdAndEventId(1L, "alias")).thenReturn(Optional.of(alias));
        service.delete(1L, "canonical");
        verify(repository).deleteAll(List.of(canonical, alias));
    }

    @Test
    void deleteRemovesStoredFavoriteEvenWhenEventDisappearedFromUpstream() {
        FavoriteEntity removed = new FavoriteEntity(1L, "removed-event", "종료된 행사",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), "문화회관", Instant.now());
        when(repository.findByMemberIdAndEventId(1L, "removed-event")).thenReturn(Optional.of(removed));
        when(events.canonicalEventId("removed-event"))
                .thenThrow(BusinessException.notFound("존재하지 않는 행사입니다."));

        service.delete(1L, "removed-event");

        verify(repository).deleteAll(List.of(removed));
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
