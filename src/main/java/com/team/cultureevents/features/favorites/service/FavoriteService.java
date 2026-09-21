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
import java.util.List;

/** 브라우저 식별자(X-Client-Id) 기준 찜 CRUD. */
@Service
@Transactional
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final EventService eventService;

    public FavoriteService(FavoriteRepository favoriteRepository, EventService eventService) {
        this.favoriteRepository = favoriteRepository;
        this.eventService = eventService;
    }

    public FavoriteResponseDTO save(String clientId, FavoriteRequestDTO request) {
        String browserKey = requireClientId(clientId);
        String eventId = request == null || request.eventId() == null ? "" : request.eventId().trim();
        if (eventId.isBlank()) {
            throw BusinessException.badRequest("eventId는 필수입니다.");
        }
        if (favoriteRepository.findByBrowserKeyAndEventId(browserKey, eventId).isPresent()) {
            throw BusinessException.conflict("이미 저장된 행사입니다.");
        }

        EventDetailResponseDTO detail = eventService.getDetail(eventId);
        FavoriteEntity saved = favoriteRepository.save(new FavoriteEntity(
                browserKey,
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
    public List<FavoriteResponseDTO> list(String clientId, String month) {
        String browserKey = requireClientId(clientId);
        List<FavoriteEntity> items = favoriteRepository.findByBrowserKeyOrderBySavedAtDesc(browserKey);
        if (month == null || month.isBlank()) {
            return items.stream().map(FavoriteResponseDTO::fromEntity).toList();
        }
        YearMonth yearMonth = parseMonth(month);
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();
        return items.stream()
                .filter(item -> EventDates.closedRange(item.getStartDate(), item.getEndDate())
                        .map(range -> range.overlaps(from, to))
                        .orElse(false))
                .map(FavoriteResponseDTO::fromEntity)
                .toList();
    }

    public void delete(String clientId, String eventId) {
        String browserKey = requireClientId(clientId);
        String id = eventId == null ? "" : eventId.trim();
        if (id.isBlank()) {
            throw BusinessException.badRequest("eventId는 필수입니다.");
        }
        FavoriteEntity existing = favoriteRepository.findByBrowserKeyAndEventId(browserKey, id)
                .orElseThrow(() -> BusinessException.notFound("저장된 행사가 없습니다."));
        favoriteRepository.delete(existing);
    }

    private static String requireClientId(String clientId) {
        String key = clientId == null ? "" : clientId.trim();
        if (key.isBlank()) {
            throw BusinessException.badRequest("X-Client-Id 헤더가 필요합니다.");
        }
        if (key.length() > 64) {
            throw BusinessException.badRequest("X-Client-Id는 64자 이하여야 합니다.");
        }
        return key;
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
