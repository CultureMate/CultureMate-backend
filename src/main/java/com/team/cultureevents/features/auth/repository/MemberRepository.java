package com.team.cultureevents.features.auth.repository;

import com.team.cultureevents.features.auth.domain.entity.MemberEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<MemberEntity, Long> {
    Optional<MemberEntity> findByKakaoId(String kakaoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MemberEntity m where m.memberId = :memberId")
    Optional<MemberEntity> findByIdForUpdate(@Param("memberId") Long memberId);
}
