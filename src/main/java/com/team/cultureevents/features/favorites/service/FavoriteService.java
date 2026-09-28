package com.team.cultureevents.features.favorites.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.commons.util.EventDates;
import com.team.cultureevents.features.events.domain.dto.EventDetailResponseDTO;
import com.team.cultureevents.features.events.service.EventService;
import com.team.cultureevents.features.favorites.domain.dto.FavoriteRequestDTO;
import com.team.cultureevents.features.favorites.domain.dto.FavoriteResponseDTO;
import com.team.cultureevents.features.favorites.domain.entity.FavoriteEntity;
import com.team.cultureevents.features.favorites.repository.FavoriteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 로그인 회원(memberId) 기준 관심행사 CRUD. */
@Service
@Transactional
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final EventService eventService;

    public FavoriteService(FavoriteRepository favoriteRepository, EventService eventService) {
        this.favoriteRepository = favoriteRepository;
        this.eventService = eventService;
    }

    public FavoriteResponseDTO save(Long memberId, FavoriteRequestDTO request) {
        String eventId = request == null || request.eventId() == null ? "" : request.eventId().trim();
        if (eventId.isBlank()) {
            throw BusinessException.badRequest("eventId는 필수입니다.");
        }
        String canonical = eventService.canonicalEventId(eventId);
        if (!findAllSaved(memberId, canonical).isEmpty()) {
            throw BusinessException.conflict("이미 저장된 행사입니다.");
        }

        EventDetailResponseDTO detail = eventService.getDetail(canonical);
        FavoriteEntity saved = favoriteRepository.save(new FavoriteEntity(
                memberId,
                detail.eventId(),
                clip(blankToTitle(detail.title()), 300),
                EventDates.parseFlexible(detail.startDate()),
                EventDates.parseFlexible(detail.endDate()),
                clip(emptyToBlank(detail.place()), 300),
                Instant.now()
        ));
        return FavoriteResponseDTO.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<FavoriteResponseDTO> list(Long memberId, String month) {
        List<FavoriteEntity> items = favoriteRepository.findByMemberIdOrderBySavedAtDesc(memberId);
        if (month != null && !month.isBlank()) {
            YearMonth yearMonth = parseMonth(month);
            LocalDate from = yearMonth.atDay(1);
            LocalDate to = yearMonth.atEndOfMonth();
            items = items.stream()
                    .filter(item -> EventDates.closedRange(item.getStartDate(), item.getEndDate())
                            .map(range -> range.overlaps(from, to))
                            .orElse(false))
                    .toList();
        }
        Map<String, FavoriteEntity> unique = new LinkedHashMap<>();
        for (FavoriteEntity item : items) {
            String canonical = canonicalKey(item.getEventId());
            FavoriteEntity current = unique.get(canonical);
            if (current == null || (!canonical.equals(current.getEventId()) && canonical.equals(item.getEventId()))) {
                unique.put(canonical, item);
            }
        }
        return unique.entrySet().stream()
                .map(entry -> toResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    public void delete(Long memberId, String eventId) {
        String id = eventId == null ? "" : eventId.trim();
        if (id.isBlank()) {
            throw BusinessException.badRequest("eventId는 필수입니다.");
        }
        // 외부 행사 목록에서 사라진 행사도 저장된 스냅샷 자체는 삭제할 수 있어야 한다.
        var exact = favoriteRepository.findByMemberIdAndEventId(memberId, id);
        List<FavoriteEntity> saved;
        try {
            saved = findAllSaved(memberId, eventService.canonicalEventId(id));
        } catch (BusinessException ex) {
            if (exact.isPresent()) {
                favoriteRepository.deleteAll(List.of(exact.get()));
                return;
            }
            if ("NOT_FOUND".equals(ex.getCode())) {
                throw BusinessException.notFound("저장된 행사가 없습니다.");
            }
            throw ex;
        }
        if (saved.isEmpty()) {
            throw BusinessException.notFound("저장된 행사가 없습니다.");
        }
        favoriteRepository.deleteAll(saved);
    }

    private List<FavoriteEntity> findAllSaved(Long memberId, String canonicalEventId) {
        List<FavoriteEntity> found = new ArrayList<>();
        for (String id : eventService.eventIdsIncludingAliases(canonicalEventId)) {
            favoriteRepository.findByMemberIdAndEventId(memberId, id).ifPresent(found::add);
        }
        return found;
    }

    private String canonicalKey(String eventId) {
        try {
            return eventService.canonicalEventId(eventId);
        } catch (BusinessException ex) {
            return eventId;
        }
    }

    private static FavoriteResponseDTO toResponse(String canonicalEventId, FavoriteEntity item) {
        FavoriteResponseDTO dto = FavoriteResponseDTO.fromEntity(item);
        if (canonicalEventId.equals(dto.eventId())) {
            return dto;
        }
        return new FavoriteResponseDTO(
                canonicalEventId, dto.title(), dto.startDate(), dto.endDate(), dto.place(), dto.savedAt());
    }

    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException ex) {
            throw BusinessException.badRequest("month는 yyyy-MM 형식이어야 합니다.");
        }
    }

    private static String blankToTitle(String title) {
        return title == null || title.isBlank() ? "제목 없음" : title;
    }

    private static String emptyToBlank(String value) {
        return value == null ? "" : value;
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
