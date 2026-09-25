package com.team.cultureevents.features.auth.repository;

import com.team.cultureevents.features.auth.domain.entity.AuthSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, String> {
    @Override
    @EntityGraph(attributePaths = "member")
    Optional<AuthSessionEntity> findById(String sessionId);

    void deleteByMember_MemberId(Long memberId);
}
