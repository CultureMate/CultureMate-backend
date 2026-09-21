package com.team.cultureevents.features.auth.ctrl;

import com.team.cultureevents.features.auth.domain.entity.MemberEntity;
import com.team.cultureevents.features.auth.service.AuthService;
import com.team.cultureevents.features.auth.service.CurrentMemberService;
import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.commons.handler.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String STATE_COOKIE = "CULTUREMATE_OAUTH_STATE";

    private final AuthService auth;
    private final CurrentMemberService currentMember;
    private final AppProperties properties;

    public AuthController(AuthService auth, CurrentMemberService currentMember, AppProperties properties) {
        this.auth = auth;
        this.currentMember = currentMember;
        this.properties = properties;
    }

    @GetMapping("/kakao/start")
    public ResponseEntity<Void> start(HttpServletRequest request) {
        String state = UUID.randomUUID().toString();
        String url = auth.authorizationUrl(state);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cookie(STATE_COOKIE, state, "/api/auth/kakao", Duration.ofMinutes(5), request).toString())
                .location(URI.create(url))
                .build();
    }

    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error,
                                         HttpServletRequest request) {
        ResponseCookie clearState = cookie(STATE_COOKIE, "", "/api/auth/kakao", Duration.ZERO, request);
        if (error != null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.SET_COOKIE, clearState.toString())
                    .location(URI.create(frontendUrl("login=cancelled")))
                    .build();
        }
        var savedState = WebUtils.getCookie(request, STATE_COOKIE);
        if (state == null || savedState == null || !state.equals(savedState.getValue())) {
            throw BusinessException.badRequest("카카오 로그인 state가 일치하지 않습니다. 다시 시작해 주세요.");
        }
        String sessionId = auth.login(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, clearState.toString(),
                        cookie(CurrentMemberService.SESSION_COOKIE, sessionId, "/api", AuthService.SESSION_AGE, request).toString())
                .location(URI.create(frontendUrl("login=success")))
                .build();
    }

    @GetMapping("/me")
    public MeResponse me(HttpServletRequest request) {
        MemberEntity member = currentMember.requireMember(request);
        return new MeResponse(member.getMemberId(), member.getNickname(), member.getResidence());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var session = WebUtils.getCookie(request, CurrentMemberService.SESSION_COOKIE);
        auth.logout(session == null ? null : session.getValue());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie(CurrentMemberService.SESSION_COOKIE, "", "/api", Duration.ZERO, request).toString())
                .build();
    }

    private ResponseCookie cookie(String name, String value, String path, Duration age, HttpServletRequest request) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path(path)
                .maxAge(age)
                .build();
    }

    private String frontendUrl(String query) {
        String origin = properties.frontendUrl();
        if (origin == null || origin.isBlank()) {
            origin = properties.cors().allowedOrigin().split(",")[0].trim();
        }
        return origin.replaceAll("/+$", "") + "/?" + query;
    }

    public record MeResponse(Long memberId, String nickname, String residence) {}
}
