package com.team.cultureevents.features.main.ctrl;

import com.team.cultureevents.features.main.domain.dto.HomeEventsResponseDTO;
import com.team.cultureevents.features.main.service.MainHomeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 홈 HOT / 다가오는 근처. FE: GET /api/main/hot-events · upcoming-events */
@RestController
@RequestMapping("/api/main")
public class MainHomeController {

    private final MainHomeService mainHomeService;

    public MainHomeController(MainHomeService mainHomeService) {
        this.mainHomeService = mainHomeService;
    }

    @GetMapping("/hot-events")
    public ResponseEntity<HomeEventsResponseDTO> hotEvents(
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(mainHomeService.hotEvents(limit));
    }

    @GetMapping("/upcoming-events")
    public ResponseEntity<HomeEventsResponseDTO> upcomingEvents(
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(mainHomeService.upcomingEvents(limit));
    }
}
