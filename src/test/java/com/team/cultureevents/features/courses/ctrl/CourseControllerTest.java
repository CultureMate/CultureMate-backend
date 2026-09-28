package com.team.cultureevents.features.courses.ctrl;

import com.team.cultureevents.features.auth.domain.entity.MemberEntity;
import com.team.cultureevents.features.auth.service.CurrentMemberService;
import com.team.cultureevents.features.commons.handler.BusinessException;
import com.team.cultureevents.features.commons.handler.GlobalExceptionHandler;
import com.team.cultureevents.features.courses.domain.dto.SharedCourseResponseDTO;
import com.team.cultureevents.features.courses.service.CourseService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseControllerTest {

    private static final String SHARE_ID = "0123456789abcdef0123456789abcdef";

    private final CourseService courses = mock(CourseService.class);
    private final CurrentMemberService currentMember = mock(CurrentMemberService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CourseController(courses, currentMember))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void sharedCourseRequiresLogin() throws Exception {
        when(currentMember.requireMember(any())).thenThrow(
                new BusinessException("UNAUTHORIZED", "로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));

        mvc.perform(get("/api/courses/shared/" + SHARE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(courses);
    }

    @Test
    void loggedInMemberCanOpenSomeoneElsesSharedCourse() throws Exception {
        when(currentMember.requireMember(any())).thenReturn(mock(MemberEntity.class));
        when(courses.getShared(SHARE_ID)).thenReturn(new SharedCourseResponseDTO(
                "공유 코스", Instant.parse("2026-09-28T00:00:00Z"), Instant.parse("2026-09-28T00:00:00Z"), List.of()));

        mvc.perform(get("/api/courses/shared/" + SHARE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("공유 코스"));
    }
}
