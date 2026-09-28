package com.team.cultureevents.features.courses.ctrl;

import com.team.cultureevents.features.auth.service.CurrentMemberService;
import com.team.cultureevents.features.courses.domain.dto.CourseCreateRequestDTO;
import com.team.cultureevents.features.courses.domain.dto.CourseDetailResponseDTO;
import com.team.cultureevents.features.courses.domain.dto.CourseFavoriteRequestDTO;
import com.team.cultureevents.features.courses.domain.dto.CourseShareResponseDTO;
import com.team.cultureevents.features.courses.domain.dto.CourseSummaryResponseDTO;
import com.team.cultureevents.features.courses.domain.dto.CourseUpdateRequestDTO;
import com.team.cultureevents.features.courses.domain.dto.SharedCourseResponseDTO;
import com.team.cultureevents.features.courses.service.CourseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;
    private final CurrentMemberService currentMember;

    public CourseController(CourseService courseService, CurrentMemberService currentMember) {
        this.courseService = courseService;
        this.currentMember = currentMember;
    }

    @PostMapping
    public ResponseEntity<CourseDetailResponseDTO> create(
            @RequestBody CourseCreateRequestDTO body,
            HttpServletRequest request
    ) {
        Long memberId = currentMember.requireMember(request).getMemberId();
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.create(memberId, body));
    }

    @GetMapping
    public ResponseEntity<List<CourseSummaryResponseDTO>> list(
            @RequestParam(defaultValue = "false") boolean favorite,
            HttpServletRequest request
    ) {
        Long memberId = currentMember.requireMember(request).getMemberId();
        return ResponseEntity.ok(courseService.list(memberId, favorite));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<CourseDetailResponseDTO> get(
            @PathVariable Long courseId,
            HttpServletRequest request
    ) {
        Long memberId = currentMember.requireMember(request).getMemberId();
        return ResponseEntity.ok(courseService.get(memberId, courseId));
    }

    @PutMapping("/{courseId}")
    public ResponseEntity<CourseDetailResponseDTO> update(
            @PathVariable Long courseId,
            @RequestBody CourseUpdateRequestDTO body,
            HttpServletRequest request
    ) {
        Long memberId = currentMember.requireMember(request).getMemberId();
        return ResponseEntity.ok(courseService.update(memberId, courseId, body));
    }

    @PutMapping("/{courseId}/favorite")
    public ResponseEntity<CourseSummaryResponseDTO> favorite(
            @PathVariable Long courseId,
            @RequestBody CourseFavoriteRequestDTO body,
            HttpServletRequest request
    ) {
        Long memberId = currentMember.requireMember(request).getMemberId();
        return ResponseEntity.ok(courseService.setFavorite(memberId, courseId, body));
    }

    @DeleteMapping("/{courseId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long courseId,
            HttpServletRequest request
    ) {
        Long memberId = currentMember.requireMember(request).getMemberId();
        courseService.delete(memberId, courseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{courseId}/share")
    public ResponseEntity<CourseShareResponseDTO> enableShare(
            @PathVariable Long courseId,
            HttpServletRequest request
    ) {
        Long memberId = currentMember.requireMember(request).getMemberId();
        return ResponseEntity.ok(courseService.enableShare(memberId, courseId));
    }

    @DeleteMapping("/{courseId}/share")
    public ResponseEntity<Void> disableShare(
            @PathVariable Long courseId,
            HttpServletRequest request
    ) {
        Long memberId = currentMember.requireMember(request).getMemberId();
        courseService.disableShare(memberId, courseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shared/{shareId}")
    public ResponseEntity<SharedCourseResponseDTO> shared(
            @PathVariable String shareId,
            HttpServletRequest request
    ) {
        currentMember.requireMember(request);
        return ResponseEntity.ok(courseService.getShared(shareId));
    }
}
