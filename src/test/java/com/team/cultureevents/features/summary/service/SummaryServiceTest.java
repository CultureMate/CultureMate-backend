package com.team.cultureevents.features.summary.service;

import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.events.domain.dto.EventDetailResponseDTO;
import com.team.cultureevents.features.events.service.EventService;
import com.team.cultureevents.features.summary.OpenAiClient;
import com.team.cultureevents.features.summary.domain.dto.SummaryResponseDTO;
import com.team.cultureevents.features.summary.domain.entity.AiSummaryEntity;
import com.team.cultureevents.features.summary.repository.AiSummaryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SummaryServiceTest {

    private final AiSummaryRepository summaries = mock(AiSummaryRepository.class);
    private final EventService events = mock(EventService.class);
    private final OpenAiClient openAi = mock(OpenAiClient.class);
    private final SummaryService service = new SummaryService(summaries, events, openAi);

    @Test
    void savedSummaryDoesNotCallOpenAi() {
        AiSummaryEntity saved = new AiSummaryEntity("e1", "저장된 소개", Instant.parse("2026-09-01T00:00:00Z"));
        when(summaries.findById("e1")).thenReturn(Optional.of(saved));

        SummaryResponseDTO result = service.createOrGet("e1");

        assertThat(result.summary()).isEqualTo("저장된 소개");
        assertThat(result.createdAt()).isEqualTo(saved.getCreatedAt());
        verify(openAi, never()).chat(anyString(), anyString());
    }

    @Test
    void missingSummaryIsGeneratedAndSaved() {
        when(summaries.findById("e1")).thenReturn(Optional.empty());
        when(events.getDetail("e1")).thenReturn(detail());
        when(openAi.chat(anyString(), anyString())).thenReturn("새 소개문");
        when(summaries.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SummaryResponseDTO result = service.createOrGet("e1");

        assertThat(result.eventId()).isEqualTo("e1");
        assertThat(result.summary()).isEqualTo("새 소개문");
        verify(summaries).save(any(AiSummaryEntity.class));
    }

    @Test
    void unknownEventIs404() {
        when(summaries.findById("missing")).thenReturn(Optional.empty());
        when(events.getDetail("missing")).thenThrow(BusinessException.notFound("행사가 없습니다."));

        assertThatThrownBy(() -> service.createOrGet("missing"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
        verify(openAi, never()).chat(anyString(), anyString());
    }

    @Test
    void openAiFailureIs503AndDoesNotSave() {
        when(summaries.findById("e1")).thenReturn(Optional.empty());
        when(events.getDetail("e1")).thenReturn(detail());
        when(openAi.chat(anyString(), anyString()))
                .thenThrow(new BusinessException("AI_UNAVAILABLE", "실패", HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> service.createOrGet("e1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AI_UNAVAILABLE");
        verify(summaries, never()).save(any());
    }

    @Test
    void duplicateSaveReturnsTheRowThatWon() {
        AiSummaryEntity winner = new AiSummaryEntity("e1", "먼저 저장된 소개", Instant.parse("2026-09-01T00:00:00Z"));
        when(summaries.findById("e1")).thenReturn(Optional.empty(), Optional.empty(), Optional.of(winner));
        when(events.getDetail("e1")).thenReturn(detail());
        when(openAi.chat(anyString(), anyString())).thenReturn("늦게 만든 소개");
        when(summaries.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        SummaryResponseDTO result = service.createOrGet("e1");

        assertThat(result.summary()).isEqualTo("먼저 저장된 소개");
    }

    @Test
    void concurrentRequestsGenerateOnce() throws Exception {
        AtomicReference<AiSummaryEntity> stored = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(summaries.findById("e1")).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(events.getDetail("e1")).thenReturn(detail());
        when(openAi.chat(anyString(), anyString())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            entered.countDown();
            release.await();
            return "한 번만 생성";
        });
        when(summaries.save(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        Thread first = new Thread(() -> service.createOrGet("e1"));
        first.start();
        entered.await();
        Thread second = new Thread(() -> service.createOrGet("e1"));
        second.start();
        Thread.sleep(150);
        release.countDown();
        first.join(2000);
        second.join(2000);

        assertThat(calls).hasValue(1);
        verify(openAi, times(1)).chat(anyString(), anyString());
    }

    private static EventDetailResponseDTO detail() {
        return new EventDetailResponseDTO(
                "e1", "제목", "전시", "마포구", "장소",
                "2026-09-01", "2026-09-30", "무료", "서울시",
                "https://example.com", null, 0);
    }
}
