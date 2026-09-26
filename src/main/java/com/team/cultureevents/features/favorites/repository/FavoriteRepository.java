package com.team.cultureevents.features.favorites.repository;

import com.team.cultureevents.features.favorites.domain.entity.FavoriteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<FavoriteEntity, Long> {

    List<FavoriteEntity> findByMemberIdOrderBySavedAtDesc(Long memberId);

    Optional<FavoriteEntity> findByMemberIdAndEventId(Long memberId, String eventId);

    long countByMemberId(Long memberId);

    void deleteByMemberId(Long memberId);
}
