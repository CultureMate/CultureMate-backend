package com.team.cultureevents.features.favorites.ctrl;

import com.team.cultureevents.features.auth.service.CurrentMemberService;
import com.team.cultureevents.features.favorites.domain.dto.FavoriteRequestDTO;
import com.team.cultureevents.features.favorites.domain.dto.FavoriteResponseDTO;
import com.team.cultureevents.features.favorites.service.FavoriteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 관심행사. 모든 요청에 로그인 세션 쿠키가 필요하다(미로그인 401). */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final CurrentMemberService currentMember;

    public FavoriteController(FavoriteService favoriteService, CurrentMemberService currentMember) {
        this.favoriteService = favoriteService;
        this.currentMember = currentMember;
    }

    @PostMapping
    public ResponseEntity<FavoriteResponseDTO> save(
            @Valid @RequestBody FavoriteRequestDTO body,
            HttpServletRequest request
    ) {
        Long memberId = currentMember.requireMember(request).getMemberId();
        return ResponseEntity.status(HttpStatus.CREATED).body(favoriteService.save(memberId, body));
    }

    @GetMapping
    public ResponseEntity<List<FavoriteResponseDTO>> list(
            @RequestParam(required = false) String month,
            HttpServletRequest request
    ) {
        Long memberId = currentMember.requireMember(request).getMemberId();
        return ResponseEntity.status(HttpStatus.OK).body(favoriteService.list(memberId, month));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> delete(
            @PathVariable String eventId,
            HttpServletRequest request
    ) {
        favoriteService.delete(currentMember.requireMember(request).getMemberId(), eventId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /** 문화포털 URL형 eventId 삭제용. 기존 경로 방식도 유지한다. */
    @DeleteMapping
    public ResponseEntity<Void> deleteByQuery(
            @RequestParam String eventId,
            HttpServletRequest request
    ) {
        favoriteService.delete(currentMember.requireMember(request).getMemberId(), eventId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
