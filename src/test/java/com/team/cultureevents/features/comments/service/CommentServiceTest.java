package com.team.cultureevents.features.comments.service;

import com.team.cultureevents.features.comments.domain.dto.CommentResponseDTO;
import com.team.cultureevents.features.comments.domain.entity.CommentEntity;
import com.team.cultureevents.features.comments.repository.CommentRepository;
import com.team.cultureevents.features.commons.handler.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    CommentRepository commentRepository;

    @InjectMocks
    CommentService commentService;

    @Test
    void createSavesTrimmedContent() {
        when(commentRepository.save(any())).thenAnswer(invocation -> {
            CommentEntity entity = invocation.getArgument(0);
            return entity;
        });

        CommentResponseDTO created = commentService.create("event-1", 7L, null, "  안녕하세요  ");

        ArgumentCaptor<CommentEntity> captor = ArgumentCaptor.forClass(CommentEntity.class);
        verify(commentRepository).save(captor.capture());
        assertEquals("안녕하세요", captor.getValue().getContent());
        assertEquals(7L, created.memberId());
        assertEquals("event-1", created.eventId());
    }

    @Test
    void updateRejectsOtherMember() {
        CommentEntity existing = new CommentEntity("event-1", 1L, null, "원문", Instant.now());
        when(commentRepository.findById(10L)).thenReturn(Optional.of(existing));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commentService.update(10L, 2L, "수정"));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void listReturnsMappedDtos() {
        CommentEntity existing = new CommentEntity("event-1", 1L, null, "원문", Instant.now());
        when(commentRepository.findByEventIdOrderByCreatedAtAsc("event-1"))
                .thenReturn(List.of(existing));

        List<CommentResponseDTO> list = commentService.list("event-1");
        assertEquals(1, list.size());
        assertEquals("원문", list.get(0).content());
    }
}
