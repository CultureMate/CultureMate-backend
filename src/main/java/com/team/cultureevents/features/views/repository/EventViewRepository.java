package com.team.cultureevents.features.views.repository;

import com.team.cultureevents.features.views.domain.entity.EventViewEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventViewRepository extends JpaRepository<EventViewEntity, String> {
}
