package com.team.cultureevents.features.places.repository;

import com.team.cultureevents.features.places.domain.entity.PlacesApiUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface PlacesApiUsageRepository extends JpaRepository<PlacesApiUsageEntity, LocalDate> {
}
