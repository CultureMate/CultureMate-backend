package com.team.cultureevents.features.summary.service;

import com.team.cultureevents.features.events.domain.dto.EventDetailResponseDTO;
import com.team.cultureevents.features.events.service.EventService;
import com.team.cultureevents.features.summary.OpenAiClient;
import com.team.cultureevents.features.summary.domain.dto.SummaryResponseDTO;
import com.team.cultureevents.features.summary.domain.entity.AiSummaryEntity;
import com.team.cultureevents.features.summary.repository.AiSummaryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 행사 AI 소개문. 저장된 게 있으면 재사용하고, 없으면 OpenAI로 생성 후 저장한다.
 */
@Service
public class SummaryService {

    private final AiSummaryRepository aiSummaryRepository;
    private final EventService eventService;
    private final OpenAiClient openAiClient;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public SummaryService(AiSummaryRepository aiSummaryRepository,
                           EventService eventService,
                           OpenAiClient openAiClient) {
        this.aiSummaryRepository = aiSummaryRepository;
        this.eventService = eventService;
        this.openAiClient = openAiClient;
    }

    public SummaryResponseDTO createOrGet(String rawEventId) {
        String eventId = eventService.canonicalEventId(rawEventId);
        return findStored(eventId)
                .map(saved -> saved.getEventId().equals(eventId)
                        ? SummaryResponseDTO.fromEntity(saved)
                        : copyToCanonical(eventId, saved))
                .orElseGet(() -> generateAndSave(eventId));
    }

    private Optional<AiSummaryEntity> findStored(String canonicalEventId) {
        Optional<AiSummaryEntity> direct = aiSummaryRepository.findById(canonicalEventId);
        if (direct.isPresent()) {
            return direct;
        }
        for (String id : eventService.eventIdsIncludingAliases(canonicalEventId)) {
            if (canonicalEventId.equals(id)) {
                continue;
            }
            Optional<AiSummaryEntity> alias = aiSummaryRepository.findById(id);
            if (alias.isPresent()) {
                return alias;
            }
        }
        return Optional.empty();
    }

    private SummaryResponseDTO copyToCanonical(String canonicalEventId, AiSummaryEntity alias) {
        synchronized (locks.computeIfAbsent(canonicalEventId, id -> new Object())) {
            var already = aiSummaryRepository.findById(canonicalEventId);
            if (already.isPresent()) {
                return SummaryResponseDTO.fromEntity(already.get());
            }
            AiSummaryEntity copied = new AiSummaryEntity(canonicalEventId, alias.getSummary(), alias.getCreatedAt());
            try {
                aiSummaryRepository.save(copied);
                return SummaryResponseDTO.fromEntity(copied);
            } catch (DataIntegrityViolationException duplicated) {
                return aiSummaryRepository.findById(canonicalEventId)
                        .map(SummaryResponseDTO::fromEntity)
                        .orElseThrow(() -> duplicated);
            }
        }
    }

    private SummaryResponseDTO generateAndSave(String eventId) {
        synchronized (locks.computeIfAbsent(eventId, id -> new Object())) {
            var saved = aiSummaryRepository.findById(eventId);
            if (saved.isPresent()) {
                return SummaryResponseDTO.fromEntity(saved.get());
            }
            return generateAndSaveLocked(eventId);
        }
    }

    private SummaryResponseDTO generateAndSaveLocked(String eventId) {
        // eventId가 존재하지 않으면 EventService가 404를 던짐
        EventDetailResponseDTO event = eventService.getDetail(eventId);

        String systemPrompt = """
                너는 서울시 문화행사를 소개하는 카피라이터야.
                과장하지 말고, 존댓말로 2~3문장만 작성해.
                """;

        String userPrompt = """
                다음 행사를 소개하는 문장을 만들어줘.
                <행사정보>
                - 제목: %s
                - 분류: %s
                - 장소: %s
                - 기간: %s ~ %s
                - 요금: %s
                - 주최: %s
                </행사정보>
                """.formatted(
                        event.title(), event.category(), event.place(),
                        event.startDate(), event.endDate(),
                        event.fee(), event.organization());

        String summary = openAiClient.chat(systemPrompt, userPrompt);

        AiSummaryEntity entity = new AiSummaryEntity(eventId, summary, Instant.now());
        try {
            aiSummaryRepository.save(entity);
            return SummaryResponseDTO.fromEntity(entity);
        } catch (DataIntegrityViolationException duplicated) {
            return aiSummaryRepository.findById(eventId)
                    .map(SummaryResponseDTO::fromEntity)
                    .orElseThrow(() -> duplicated);
        }
    }
}
