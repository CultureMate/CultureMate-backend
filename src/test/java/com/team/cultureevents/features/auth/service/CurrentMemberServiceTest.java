package com.team.cultureevents.features.auth.service;

import com.team.cultureevents.features.auth.domain.entity.AuthSessionEntity;
import com.team.cultureevents.features.auth.domain.entity.MemberEntity;
import com.team.cultureevents.features.auth.repository.AuthSessionRepository;
import com.team.cultureevents.features.commons.handler.BusinessException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentMemberServiceTest {
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private final CurrentMemberService current = new CurrentMemberService(sessions);

    @Test
    void validSessionReturnsMember() {
        MemberEntity member = new MemberEntity("1", "tester");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CurrentMemberService.SESSION_COOKIE, "session"));
        when(sessions.findById("session"))
                .thenReturn(Optional.of(new AuthSessionEntity("session", member, Instant.now().plusSeconds(60))));
        assertThat(current.requireMember(request)).isSameAs(member);
    }

    @Test
    void expiredSessionIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CurrentMemberService.SESSION_COOKIE, "expired"));
        when(sessions.findById("expired")).thenReturn(Optional.of(
                new AuthSessionEntity("expired", new MemberEntity("1", "tester"), Instant.now().minusSeconds(1))));
        assertThatThrownBy(() -> current.requireMember(request))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo("UNAUTHORIZED"));
    }
}
