package com.team.cultureevents.features.comments.service;

import com.team.cultureevents.features.auth.domain.entity.MemberEntity;
import com.team.cultureevents.features.auth.repository.MemberRepository;
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

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    CommentRepository commentRepository;

    @Mock
    MemberRepository memberRepository;

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

    @Test
    void listIncludesNicknameAndNullForWithdrawnMember() throws Exception {
        CommentEntity byMember = new CommentEntity("event-1", 1L, null, "안녕", Instant.now());
        CommentEntity byWithdrawn = new CommentEntity("event-1", 2L, null, "탈퇴", Instant.now());
        when(commentRepository.findByEventIdOrderByCreatedAtAsc("event-1"))
                .thenReturn(List.of(byMember, byWithdrawn));
        MemberEntity member = new MemberEntity("kakao-1", "컬처러버");
        Field id = MemberEntity.class.getDeclaredField("memberId");
        id.setAccessible(true);
        id.set(member, 1L);
        when(memberRepository.findAllById(any())).thenReturn(List.of(member));

        List<CommentResponseDTO> list = commentService.list("event-1");

        assertEquals("컬처러버", list.get(0).nickname());
        assertNull(list.get(1).nickname());
    }

    @Test
    void deleteAlsoRemovesReplies() {
        CommentEntity parent = new CommentEntity("event-1", 1L, null, "부모", Instant.now());
        CommentEntity reply = new CommentEntity("event-1", 2L, 10L, "대댓글", Instant.now());
        when(commentRepository.findById(10L)).thenReturn(Optional.of(parent));
        when(commentRepository.findByParentId(10L)).thenReturn(List.of(reply));

        commentService.delete(10L, 1L);

        verify(commentRepository).deleteAll(List.of(reply));
        verify(commentRepository).delete(parent);
    }
}
