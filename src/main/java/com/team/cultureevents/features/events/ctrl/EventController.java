package com.team.cultureevents.features.events.ctrl;

import com.team.cultureevents.features.events.domain.dto.EventDetailResponseDTO;
import com.team.cultureevents.features.events.domain.dto.EventListResponseDTO;
import com.team.cultureevents.features.events.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 상세: eventId에 URL(슬래시)이 있으면 반드시 query 사용
 *   GET /api/events/detail?eventId={url-encoded}
 * 슬래시 없는 키만 path 사용 가능
 *   GET /api/events/{eventId}
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public ResponseEntity<EventListResponseDTO> listEvents(
            @RequestParam(required = false) List<String> district,
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) List<String> date,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(eventService.list(district, category, date, keyword, from, to, page, size));
    }

    @GetMapping("/detail")
    public ResponseEntity<EventDetailResponseDTO> getEventByQuery(@RequestParam("eventId") String eventId) {
        return ResponseEntity.status(HttpStatus.OK).body(eventService.getDetail(eventId));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventDetailResponseDTO> getEvent(@PathVariable String eventId) {
        return ResponseEntity.status(HttpStatus.OK).body(eventService.getDetail(eventId));
    }
}
