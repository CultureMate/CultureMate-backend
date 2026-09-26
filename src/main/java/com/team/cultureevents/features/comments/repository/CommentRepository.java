package com.team.cultureevents.features.comments.repository;

import com.team.cultureevents.features.comments.domain.entity.CommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<CommentEntity, Long> {

    List<CommentEntity> findByEventIdOrderByCreatedAtAsc(String eventId);

    List<CommentEntity> findByParentId(Long parentId);
}