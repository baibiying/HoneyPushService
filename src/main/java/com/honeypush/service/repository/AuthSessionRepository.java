package com.honeypush.service.repository;

import com.honeypush.service.entity.AuthSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, Integer> {
    Optional<AuthSessionEntity> findBySessionTokenHash(String sessionTokenHash);

    void deleteBySessionTokenHash(String sessionTokenHash);
}
