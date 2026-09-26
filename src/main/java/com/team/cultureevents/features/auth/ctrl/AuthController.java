package com.team.cultureevents.features.auth.ctrl;

import com.team.cultureevents.features.auth.domain.entity.MemberEntity;
import com.team.cultureevents.features.auth.repository.AuthSessionRepository;
import com.team.cultureevents.features.auth.repository.MemberRepository;
import com.team.cultureevents.features.auth.service.AuthService;
import com.team.cultureevents.features.auth.service.CurrentMemberService;
import com.team.cultureevents.features.commons.config.AppProperties;
import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.favorites.repository.FavoriteRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String STATE_COOKIE = "CULTUREMATE_OAUTH_STATE";

    private final AuthService auth;
    private final CurrentMemberService currentMember;
    private final AppProperties properties;
    private final MemberRepository members;
    private final AuthSessionRepository sessions;
    private final FavoriteRepository favorites;

    public AuthController(AuthService auth, CurrentMemberService currentMember, AppProperties properties,
                           MemberRepository members, AuthSessionRepository sessions, FavoriteRepository favorites) {
        this.auth = auth;
        this.currentMember = currentMember;
        this.properties = properties;
        this.members = members;
        this.sessions = sessions;
        this.favorites = favorites;
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
        return toMe(currentMember.requireMember(request));
    }

    @PutMapping("/me")
    public MeResponse updateMe(@RequestBody UpdateMeRequest request, HttpServletRequest httpRequest) {
        MemberEntity member = currentMember.requireMember(httpRequest);

        boolean hasNickname = request.nickname() != null && !request.nickname().isBlank();
        boolean hasResidence = request.residence() != null && !request.residence().isBlank();
        boolean hasInterests = request.interestCategories() != null;
        if (!hasNickname && !hasResidence && !hasInterests) {
            throw BusinessException.badRequest("수정할 닉네임, 거주지 또는 관심 카테고리를 입력해주세요.");
        }
        if (hasNickname) {
            checkLength(request.nickname(), "닉네임은");
        }
        if (hasResidence) {
            checkLength(request.residence(), "거주지는");
        }

        List<String> interests = hasInterests ? normalizeInterests(request.interestCategories()) : null;

        member.updateNickname(request.nickname());
        member.updateResidence(request.residence());
        member.updateInterestCategories(interests);
        members.save(member);

        return toMe(member);
    }

    @DeleteMapping("/me")
    @Transactional
    public ResponseEntity<Void> deleteMe(HttpServletRequest request) {
        MemberEntity member = currentMember.requireMember(request);

        sessions.deleteByMember_MemberId(member.getMemberId());
        favorites.deleteByMemberId(member.getMemberId());
        members.delete(member);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie(CurrentMemberService.SESSION_COOKIE, "", "/api", Duration.ZERO, request).toString())
                .build();
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

    private MeResponse toMe(MemberEntity member) {
        return new MeResponse(member.getMemberId(), member.getNickname(), member.getResidence(),
                member.getInterestCategories(), favorites.countByMemberId(member.getMemberId()));
    }

    /** 공백·중복 제거. 쉼표로 이어 저장하므로 쉼표는 허용하지 않는다. */
    private static List<String> normalizeInterests(List<String> raw) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) continue;
            String v = value.trim();
            if (v.contains(",") || v.length() > 20) {
                throw BusinessException.badRequest("관심 카테고리 값이 올바르지 않습니다.");
            }
            set.add(v);
        }
        if (set.size() > 10) {
            throw BusinessException.badRequest("관심 카테고리는 10개까지 선택할 수 있습니다.");
        }
        return List.copyOf(set);
    }

    private static void checkLength(String value, String label) {
        if (value.length() > 50) {
            throw BusinessException.badRequest(label + " 50자 이하입니다.");
        }
    }

    private String frontendUrl(String query) {
        String origin = properties.frontendUrl();
        if (origin == null || origin.isBlank()) {
            origin = properties.cors().allowedOrigin().split(",")[0].trim();
        }
        return origin.replaceAll("/+$", "") + "/?" + query;
    }

    public record MeResponse(Long memberId, String nickname, String residence,
                             List<String> interestCategories, long favoriteCount) {}

    public record UpdateMeRequest(String nickname, String residence, List<String> interestCategories) {}
}