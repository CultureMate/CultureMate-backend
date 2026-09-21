package com.team.cultureevents.features.auth.repository;

import com.team.cultureevents.features.auth.domain.entity.MemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<MemberEntity, Long> {
    Optional<MemberEntity> findByKakaoId(String kakaoId);
}
