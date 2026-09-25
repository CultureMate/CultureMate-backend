package com.team.cultureevents.features.auth.ctrl;

import com.team.cultureevents.features.auth.domain.entity.MemberEntity;
import com.team.cultureevents.features.auth.repository.AuthSessionRepository;
import com.team.cultureevents.features.auth.repository.MemberRepository;
import com.team.cultureevents.features.auth.service.AuthService;
import com.team.cultureevents.features.auth.service.CurrentMemberService;
import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.commons.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerMeTest {

    private final CurrentMemberService currentMember = mock(CurrentMemberService.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(
                mock(AuthService.class), currentMember, mock(AppProperties.class), members, sessions);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void updateWithoutSessionIs401() throws Exception {
        when(currentMember.requireMember(any())).thenThrow(
                new BusinessException("UNAUTHORIZED", "로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(put("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"민수\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(members, never()).save(any());
    }

    @Test
    void updateRejectsEmptyBody() throws Exception {
        when(currentMember.requireMember(any())).thenReturn(member());

        mockMvc.perform(put("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
        verify(members, never()).save(any());
    }

    @Test
    void updateRejectsNicknameOrResidenceOver50() throws Exception {
        when(currentMember.requireMember(any())).thenReturn(member());
        String tooLong = "가".repeat(51);

        mockMvc.perform(put("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAM"));

        mockMvc.perform(put("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"residence\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
        verify(members, never()).save(any());
    }

    @Test
    void updateChangesOnlyProvidedFieldWithin50() throws Exception {
        MemberEntity member = member();
        when(currentMember.requireMember(any())).thenReturn(member);
        String nickname = "가".repeat(50);

        mockMvc.perform(put("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andExpect(jsonPath("$.residence").value("마포구"));
        verify(members).save(member);
    }

    @Test
    void deleteWithoutSessionIs401() throws Exception {
        when(currentMember.requireMember(any())).thenThrow(
                new BusinessException("UNAUTHORIZED", "로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(delete("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        verify(sessions, never()).deleteByMember_MemberId(any());
        verify(members, never()).delete(any());
    }

    @Test
    void deleteRemovesSessionsAndMember() throws Exception {
        MemberEntity member = member();
        when(currentMember.requireMember(any())).thenReturn(member);

        mockMvc.perform(delete("/api/auth/me"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("CULTUREMATE_SESSION")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

        verify(sessions).deleteByMember_MemberId(7L);
        verify(members).delete(member);
    }

    private static MemberEntity member() throws Exception {
        MemberEntity member = new MemberEntity("kakao-1", "기존");
        member.updateResidence("마포구");
        Field id = MemberEntity.class.getDeclaredField("memberId");
        id.setAccessible(true);
        id.set(member, 7L);
        return member;
    }
}
