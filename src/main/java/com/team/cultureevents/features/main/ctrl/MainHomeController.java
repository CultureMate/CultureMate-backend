package com.team.cultureevents.features.main.ctrl;

import com.team.cultureevents.features.auth.domain.entity.MemberEntity;
import com.team.cultureevents.features.auth.service.CurrentMemberService;
import com.team.cultureevents.features.main.domain.dto.HomeEventsResponseDTO;
import com.team.cultureevents.features.main.service.MainHomeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 홈 HOT / 다가오는 근처. FE: GET /api/main/hot-events · upcoming-events */
@RestController
@RequestMapping("/api/main")
public class MainHomeController {

    private final MainHomeService mainHomeService;
    private final CurrentMemberService currentMember;

    public MainHomeController(MainHomeService mainHomeService, CurrentMemberService currentMember) {
        this.mainHomeService = mainHomeService;
        this.currentMember = currentMember;
    }

    @GetMapping("/hot-events")
    public ResponseEntity<HomeEventsResponseDTO> hotEvents(
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(mainHomeService.hotEvents(limit));
    }

    /** district 미지정 시 로그인 회원의 거주지, 비로그인·거주지 미설정이면 서울 전체. */
    @GetMapping("/upcoming-events")
    public ResponseEntity<HomeEventsResponseDTO> upcomingEvents(
            @RequestParam(required = false) String district,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        String target = district != null && !district.isBlank() ? district.trim()
                : currentMember.findMember(request).map(MemberEntity::getResidence).orElse(null);
        return ResponseEntity.ok(mainHomeService.upcomingEvents(target, limit));
    }
}
