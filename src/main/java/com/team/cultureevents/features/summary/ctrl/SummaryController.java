package com.team.cultureevents.features.summary.ctrl;

import com.team.cultureevents.features.summary.domain.dto.SummaryResponseDTO;
import com.team.cultureevents.features.summary.service.SummaryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @PostMapping("/{eventId}/summary")
    public ResponseEntity<SummaryResponseDTO> createOrGetSummary(@PathVariable String eventId) {
        return ResponseEntity.status(HttpStatus.OK).body(summaryService.createOrGet(eventId));
    }

    /** 문화포털 URL처럼 슬래시가 포함된 eventId는 query 파라미터로 받는다. */
    @PostMapping("/summary")
    public ResponseEntity<SummaryResponseDTO> createOrGetSummaryByQuery(@RequestParam String eventId) {
        return ResponseEntity.status(HttpStatus.OK).body(summaryService.createOrGet(eventId));
    }
}
