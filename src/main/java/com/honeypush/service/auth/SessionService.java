package com.honeypush.service.auth;

import com.honeypush.service.config.AppProperties;
import com.honeypush.service.entity.AuthSessionEntity;
import com.honeypush.service.entity.UserEntity;
import com.honeypush.service.repository.AuthSessionRepository;
import com.honeypush.service.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    private final AuthSessionRepository authSessionRepository;
    private final UserRepository userRepository;
    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public SessionService(
            AuthSessionRepository authSessionRepository,
            UserRepository userRepository,
            AppProperties appProperties) {
        this.authSessionRepository = authSessionRepository;
        this.userRepository = userRepository;
        this.appProperties = appProperties;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    public static boolean isValidEmail(String email) {
        return email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    public static boolean isValidPassword(String password) {
        return password.trim().length() >= 8;
    }

    public static String generateUserId() {
        return "usr_" + UUID.randomUUID();
    }

    public AuthUser toAuthUser(UserEntity user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getName(), user.getAvatarUrl());
    }

    @Transactional
    public SessionToken createSessionForUser(String userId) {
        String token = randomSessionToken();
        Instant expiresAt = Instant.now().plusSeconds(SessionConstants.SESSION_DURATION_SECONDS);
        AuthSessionEntity session = new AuthSessionEntity();
        session.setUserId(userId);
        session.setSessionTokenHash(hashToken(token));
        session.setExpiresAt(expiresAt);
        session.setCreatedAt(Instant.now());
        authSessionRepository.save(session);
        return new SessionToken(token, expiresAt);
    }

    public record SessionToken(String token, Instant expiresAt) {}

    public void applySessionCookie(HttpServletResponse response, String sessionToken, Instant expiresAt) {
        ResponseCookie cookie = ResponseCookie.from(SessionConstants.COOKIE_NAME, sessionToken)
                .httpOnly(true)
                .sameSite("Lax")
                .secure(appProperties.cookie().secure())
                .path("/")
                .maxAge(SessionConstants.SESSION_DURATION_SECONDS)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(SessionConstants.COOKIE_NAME, "")
                .httpOnly(true)
                .sameSite("Lax")
                .secure(appProperties.cookie().secure())
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Transactional
    public void destroySession(HttpServletRequest request) {
        readSessionToken(request).ifPresent(token -> authSessionRepository.deleteBySessionTokenHash(hashToken(token)));
    }

    @Transactional
    public Optional<AuthUser> getCurrentUser(HttpServletRequest request) {
        Optional<String> tokenOpt = readSessionToken(request);
        if (tokenOpt.isEmpty()) {
            return Optional.empty();
        }
        String hash = hashToken(tokenOpt.get());
        Optional<AuthSessionEntity> sessionOpt = authSessionRepository.findBySessionTokenHash(hash);
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }
        AuthSessionEntity session = sessionOpt.get();
        if (!session.getExpiresAt().isAfter(Instant.now())) {
            authSessionRepository.deleteBySessionTokenHash(hash);
            return Optional.empty();
        }
        return userRepository.findById(session.getUserId()).map(this::toAuthUser);
    }

    public Optional<UserEntity> getPasswordUserByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .filter(u -> "password".equals(u.getAuthProvider()));
    }

    private Optional<String> readSessionToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (SessionConstants.COOKIE_NAME.equals(cookie.getName())) {
                    String value = cookie.getValue();
                    if (value != null && !value.isBlank()) {
                        return Optional.of(value);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private String randomSessionToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
