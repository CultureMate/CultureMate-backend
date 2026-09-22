package com.team.cultureevents.features.views.ctrl;

import com.team.cultureevents.features.views.domain.dto.EventViewResponseDTO;
import com.team.cultureevents.features.views.service.EventViewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 행사 조회수. 로그인 불필요. URL형 eventId는 query 사용. */
@RestController
@RequestMapping("/api/events")
public class EventViewController {

    private final EventViewService eventViewService;

    public EventViewController(EventViewService eventViewService) {
        this.eventViewService = eventViewService;
    }

    @PostMapping("/{eventId}/views")
    public ResponseEntity<EventViewResponseDTO> increment(@PathVariable String eventId) {
        return ResponseEntity.ok(eventViewService.increment(eventId));
    }

    @PostMapping("/views")
    public ResponseEntity<EventViewResponseDTO> incrementByQuery(@RequestParam String eventId) {
        return ResponseEntity.ok(eventViewService.increment(eventId));
    }
}
