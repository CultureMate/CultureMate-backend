package com.team.cultureevents.features.views.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.events.service.EventService;
import com.team.cultureevents.features.views.domain.dto.EventViewResponseDTO;
import com.team.cultureevents.features.views.domain.entity.EventViewEntity;
import com.team.cultureevents.features.views.repository.EventViewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventViewService {

    private final EventViewRepository eventViewRepository;
    private final EventService eventService;

    public EventViewService(EventViewRepository eventViewRepository, EventService eventService) {
        this.eventViewRepository = eventViewRepository;
        this.eventService = eventService;
    }

    /** 행사가 없으면 404. 없으면 0에서 시작해 +1. */
    @Transactional
    public EventViewResponseDTO increment(String rawEventId) {
        String eventId = requireEventId(rawEventId);
        eventService.getDetail(eventId);
        EventViewEntity entity = eventViewRepository.findById(eventId)
                .orElseGet(() -> new EventViewEntity(eventId, 0));
        int count = entity.increment();
        eventViewRepository.save(entity);
        return new EventViewResponseDTO(eventId, count);
    }

    @Transactional(readOnly = true)
    public int getCount(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return 0;
        }
        return eventViewRepository.findById(eventId.trim())
                .map(EventViewEntity::getViewCount)
                .orElse(0);
    }

    private static String requireEventId(String rawEventId) {
        if (rawEventId == null || rawEventId.isBlank()) {
            throw BusinessException.badRequest("eventId는 필수입니다.");
        }
        return rawEventId.trim();
    }
}
