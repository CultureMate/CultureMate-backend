package com.team.cultureevents.features.auth.service;

import com.team.cultureevents.features.auth.domain.entity.AuthSessionEntity;
import com.team.cultureevents.features.auth.domain.entity.MemberEntity;
import com.team.cultureevents.features.auth.repository.AuthSessionRepository;
import com.team.cultureevents.features.commons.handler.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.WebUtils;

import java.time.Instant;
import java.util.Optional;

@Service
public class CurrentMemberService {
    public static final String SESSION_COOKIE = "CULTUREMATE_SESSION";

    private final AuthSessionRepository sessions;

    public CurrentMemberService(AuthSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public MemberEntity requireMember(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, SESSION_COOKIE);
        if (cookie == null || cookie.getValue().isBlank()) {
            throw unauthorized();
        }
        AuthSessionEntity session = sessions.findById(cookie.getValue()).orElseThrow(CurrentMemberService::unauthorized);
        if (!session.getExpiresAt().isAfter(Instant.now())) {
            throw unauthorized();
        }
        return session.getMember();
    }

    /** 비로그인도 허용하는 API용. 세션이 없거나 만료면 empty. */
    @Transactional(readOnly = true)
    public Optional<MemberEntity> findMember(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, SESSION_COOKIE);
        if (cookie == null || cookie.getValue().isBlank()) {
            return Optional.empty();
        }
        return sessions.findById(cookie.getValue())
                .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
                .map(AuthSessionEntity::getMember);
    }

    private static BusinessException unauthorized() {
        return new BusinessException("UNAUTHORIZED", "로그인이 필요합니다.", HttpStatus.UNAUTHORIZED);
    }
}
