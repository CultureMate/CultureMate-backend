package com.team.cultureevents.features.views.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.events.service.EventService;
import com.team.cultureevents.features.views.domain.dto.EventViewResponseDTO;
import com.team.cultureevents.features.views.domain.entity.EventViewEntity;
import com.team.cultureevents.features.views.repository.EventViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventViewServiceTest {

    @Mock
    EventViewRepository eventViewRepository;

    @Mock
    EventService eventService;

    @InjectMocks
    EventViewService eventViewService;

    @BeforeEach
    void eventIdsPassThrough() {
        lenient().when(eventService.canonicalEventId(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(eventService.eventIdsIncludingAliases(any()))
                .thenAnswer(invocation -> List.of(invocation.getArgument(0, String.class)));
    }

    @Test
    void firstIncrementStartsFromZeroThenOne() {
        when(eventViewRepository.findById("event-1")).thenReturn(Optional.empty());
        when(eventViewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EventViewResponseDTO result = eventViewService.increment("event-1");

        assertEquals(1, result.viewCount());
        ArgumentCaptor<EventViewEntity> captor = ArgumentCaptor.forClass(EventViewEntity.class);
        verify(eventViewRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getViewCount());
    }

    @Test
    void secondIncrementAddsOne() {
        when(eventViewRepository.findById("event-1")).thenReturn(Optional.of(new EventViewEntity("event-1", 3)));
        when(eventViewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(4, eventViewService.increment("event-1").viewCount());
    }

    @Test
    void unknownEventReturnsNotFound() {
        when(eventService.canonicalEventId("missing")).thenThrow(BusinessException.notFound("존재하지 않는 행사입니다."));
        assertThrows(BusinessException.class, () -> eventViewService.increment("missing"));
    }

    @Test
    void aliasViewCountStaysWhenIncrementingCanonicalEvent() {
        when(eventService.canonicalEventId("alias")).thenReturn("canonical");
        when(eventService.eventIdsIncludingAliases("canonical")).thenReturn(List.of("canonical", "alias"));
        when(eventViewRepository.findById("canonical")).thenReturn(Optional.empty());
        when(eventViewRepository.findById("alias")).thenReturn(Optional.of(new EventViewEntity("alias", 4)));
        when(eventViewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EventViewResponseDTO result = eventViewService.increment("alias");

        assertEquals("canonical", result.eventId());
        assertEquals(5, result.viewCount());
        ArgumentCaptor<EventViewEntity> captor = ArgumentCaptor.forClass(EventViewEntity.class);
        verify(eventViewRepository).save(captor.capture());
        assertEquals("canonical", captor.getValue().getEventId());
        assertEquals(1, captor.getValue().getViewCount());
    }
}
