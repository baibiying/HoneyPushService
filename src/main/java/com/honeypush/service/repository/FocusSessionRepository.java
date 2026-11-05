package com.honeypush.service.repository;

import com.honeypush.service.entity.FocusSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FocusSessionRepository extends JpaRepository<FocusSessionEntity, Integer> {
    List<FocusSessionEntity> findByUserIdOrderByCompletedAtDesc(String userId);

    List<FocusSessionEntity> findTop30ByUserIdOrderByCompletedAtDesc(String userId);
}
