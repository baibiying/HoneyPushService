package com.honeypush.service.repository;

import com.honeypush.service.entity.TaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<TaskEntity, Integer> {
    List<TaskEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<TaskEntity> findByIdAndUserId(Integer id, String userId);
}
