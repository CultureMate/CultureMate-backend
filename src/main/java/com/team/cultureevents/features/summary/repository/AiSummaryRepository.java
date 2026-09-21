package com.team.cultureevents.features.summary.repository;

import com.team.cultureevents.features.summary.domain.entity.AiSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSummaryRepository extends JpaRepository<AiSummaryEntity, String> {
}
