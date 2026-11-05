package com.honeypush.service.repository;

import com.honeypush.service.entity.UserStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserStatsRepository extends JpaRepository<UserStatsEntity, Integer> {
    Optional<UserStatsEntity> findByUserId(String userId);
}
