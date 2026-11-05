package com.honeypush.service.service;

import com.honeypush.service.auth.AuthUser;
import com.honeypush.service.auth.NodeScryptPasswordHasher;
import com.honeypush.service.auth.SessionService;
import com.honeypush.service.entity.UserEntity;
import com.honeypush.service.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class AuthRegistrationService {

    private final UserRepository userRepository;
    private final NodeScryptPasswordHasher passwordHasher;
    private final SessionService sessionService;

    public AuthRegistrationService(
            UserRepository userRepository,
            NodeScryptPasswordHasher passwordHasher,
            SessionService sessionService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.sessionService = sessionService;
    }

    @Transactional
    public Optional<UserEntity> register(String email, String password, String name) {
        String normalized = SessionService.normalizeEmail(email);
        if (userRepository.findByEmail(normalized).isPresent()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        UserEntity user = new UserEntity();
        user.setId(SessionService.generateUserId());
        user.setEmail(normalized);
        user.setPasswordHash(passwordHasher.hashPassword(password));
        user.setAuthProvider("password");
        user.setEmailVerifiedAt(now);
        user.setName(name != null && !name.isBlank() ? name.trim() : null);
        user.setAvatarUrl(null);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return Optional.of(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Optional<UserEntity> authenticate(String email, String password) {
        Optional<UserEntity> userOpt = sessionService.getPasswordUserByEmail(email);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        UserEntity user = userOpt.get();
        if (!passwordHasher.verifyPassword(password, user.getPasswordHash())) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public AuthUser toAuthUser(UserEntity user) {
        return sessionService.toAuthUser(user);
    }

    public SessionService.SessionToken createSession(String userId) {
        return sessionService.createSessionForUser(userId);
    }
}
