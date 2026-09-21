package com.team.cultureevents.features.auth.service;

import com.team.cultureevents.features.auth.domain.entity.AuthSessionEntity;
import com.team.cultureevents.features.auth.domain.entity.MemberEntity;
import com.team.cultureevents.features.auth.repository.AuthSessionRepository;
import com.team.cultureevents.features.auth.repository.MemberRepository;
import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.commons.handler.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {
    public static final Duration SESSION_AGE = Duration.ofDays(7);

    private final AppProperties.Kakao settings;
    private final KakaoApiClient kakao;
    private final MemberRepository members;
    private final AuthSessionRepository sessions;

    public AuthService(AppProperties properties, KakaoApiClient kakao,
                       MemberRepository members, AuthSessionRepository sessions) {
        this.settings = properties.kakao();
        this.kakao = kakao;
        this.members = members;
        this.sessions = sessions;
    }

    public String authorizationUrl(String state) {
        if (settings.restKey() == null || settings.restKey().isBlank()) {
            throw new BusinessException("AUTH_NOT_CONFIGURED", "KAKAO_REST_KEY 설정이 필요합니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", settings.restKey())
                .queryParam("redirect_uri", settings.redirectUri())
                .queryParam("state", state)
                .build().encode().toUriString();
    }

    @Transactional
    public String login(String code) {
        if (code == null || code.isBlank()) {
            throw BusinessException.badRequest("카카오 인가 코드가 없습니다.");
        }
        KakaoApiClient.KakaoProfile profile = kakao.fetchProfile(code);
        MemberEntity member = members.findByKakaoId(profile.id()).orElseGet(() -> new MemberEntity(profile.id(), profile.nickname()));
        member.updateNickname(profile.nickname());
        member = members.save(member);
        String sessionId = UUID.randomUUID().toString();
        sessions.save(new AuthSessionEntity(sessionId, member, Instant.now().plus(SESSION_AGE)));
        return sessionId;
    }

    @Transactional
    public void logout(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessions.deleteById(sessionId);
        }
    }
}
