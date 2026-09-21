package com.team.cultureevents.features.auth.service;

import com.team.cultureevents.features.auth.domain.entity.AuthSessionEntity;
import com.team.cultureevents.features.auth.domain.entity.MemberEntity;
import com.team.cultureevents.features.auth.repository.AuthSessionRepository;
import com.team.cultureevents.features.auth.repository.MemberRepository;
import com.team.cultureevents.features.commons.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private final KakaoApiClient kakao = mock(KakaoApiClient.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private final AppProperties props = new AppProperties(null, null, null,
            new AppProperties.Kakao("test-rest-key", "", "http://localhost:8080/api/auth/kakao/callback"),
            "http://localhost:5175");
    private final AuthService auth = new AuthService(props, kakao, members, sessions);

    @Test
    void authorizationUrlContainsRegisteredCallbackAndCsrfState() {
        String url = auth.authorizationUrl("random-state");
        assertThat(url).startsWith("https://kauth.kakao.com/oauth/authorize?")
                .contains("client_id=test-rest-key", "response_type=code", "state=random-state")
                .contains("redirect_uri=http://localhost:8080/api/auth/kakao/callback");
    }

    @Test
    void loginReusesMemberAndCreatesPersistentSession() {
        MemberEntity existing = new MemberEntity("12345", "old");
        when(kakao.fetchProfile("one-time-code")).thenReturn(new KakaoApiClient.KakaoProfile("12345", "new"));
        when(members.findByKakaoId("12345")).thenReturn(Optional.of(existing));
        when(members.save(existing)).thenReturn(existing);

        Instant before = Instant.now();
        String id = auth.login("one-time-code");

        assertThat(id).hasSize(36);
        assertThat(existing.getNickname()).isEqualTo("new");
        ArgumentCaptor<AuthSessionEntity> saved = ArgumentCaptor.forClass(AuthSessionEntity.class);
        verify(sessions).save(saved.capture());
        assertThat(saved.getValue().getSessionId()).isEqualTo(id);
        assertThat(saved.getValue().getMember()).isSameAs(existing);
        assertThat(saved.getValue().getExpiresAt()).isAfter(before.plusSeconds(6 * 24 * 3600));
    }
}
