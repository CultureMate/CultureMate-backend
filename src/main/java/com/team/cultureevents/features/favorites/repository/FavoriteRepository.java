package com.team.cultureevents.features.favorites.repository;

import com.team.cultureevents.features.favorites.domain.entity.FavoriteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<FavoriteEntity, Long> {

    List<FavoriteEntity> findByBrowserKeyOrderBySavedAtDesc(String browserKey);

    Optional<FavoriteEntity> findByBrowserKeyAndEventId(String browserKey, String eventId);

    void deleteByBrowserKeyAndEventId(String browserKey, String eventId);
}
