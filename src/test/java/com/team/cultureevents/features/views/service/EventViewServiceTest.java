package com.team.cultureevents.features.views.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.events.domain.dto.EventDetailResponseDTO;
import com.team.cultureevents.features.events.service.EventService;
import com.team.cultureevents.features.views.domain.dto.EventViewResponseDTO;
import com.team.cultureevents.features.views.domain.entity.EventViewEntity;
import com.team.cultureevents.features.views.repository.EventViewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void firstIncrementStartsFromZeroThenOne() {
        when(eventService.getDetail("event-1")).thenReturn(detail("event-1"));
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
        when(eventService.getDetail("event-1")).thenReturn(detail("event-1"));
        when(eventViewRepository.findById("event-1")).thenReturn(Optional.of(new EventViewEntity("event-1", 3)));
        when(eventViewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(4, eventViewService.increment("event-1").viewCount());
    }

    @Test
    void unknownEventReturnsNotFound() {
        when(eventService.getDetail("missing")).thenThrow(BusinessException.notFound("존재하지 않는 행사입니다."));
        assertThrows(BusinessException.class, () -> eventViewService.increment("missing"));
    }

    private static EventDetailResponseDTO detail(String eventId) {
        return new EventDetailResponseDTO(
                eventId, "t", "c", "d", "p", "2026-01-01", "2026-01-02",
                "", "", "", "", 0
        );
    }
}
