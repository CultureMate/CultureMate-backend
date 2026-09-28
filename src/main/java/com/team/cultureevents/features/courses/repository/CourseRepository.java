package com.team.cultureevents.features.courses.repository;

import com.team.cultureevents.features.courses.domain.entity.CourseEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<CourseEntity, Long> {

    List<CourseEntity> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<CourseEntity> findByMemberIdAndFavoritedTrueOrderByFavoritedAtDesc(Long memberId);

    @EntityGraph(attributePaths = "stops")
    @Query("select c from CourseEntity c where c.courseId = :courseId")
    Optional<CourseEntity> findWithStopsById(@Param("courseId") Long courseId);

    @EntityGraph(attributePaths = "stops")
    Optional<CourseEntity> findByShareId(String shareId);

    boolean existsByShareId(String shareId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourseEntity c where c.courseId = :courseId")
    Optional<CourseEntity> findByIdForUpdate(@Param("courseId") Long courseId);
}
