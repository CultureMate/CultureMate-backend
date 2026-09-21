package com.team.cultureevents.features.summary.service;

import com.team.cultureevents.features.summary.domain.dto.SummaryResponseDTO;
import com.team.cultureevents.features.summary.repository.AiSummaryRepository;
import org.springframework.stereotype.Service;

/**
 * 행사 AI 소개문. 아직 미구현이면 501.
 */
@Service
public class SummaryService {

    private final AiSummaryRepository aiSummaryRepository;

    public SummaryService(AiSummaryRepository aiSummaryRepository) {
        this.aiSummaryRepository = aiSummaryRepository;
    }

    public SummaryResponseDTO createOrGet(String eventId) {
        throw new UnsupportedOperationException("AI 소개문은 아직 구현되지 않았습니다.");
    }
}
