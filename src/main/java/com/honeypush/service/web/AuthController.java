package com.honeypush.service.web;

import com.honeypush.service.auth.SessionService;
import com.honeypush.service.entity.UserEntity;
import com.honeypush.service.repository.UserRepository;
import com.honeypush.service.service.AuthRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthRegistrationService authRegistrationService;
    private final SessionService sessionService;
    private final UserRepository userRepository;

    public AuthController(
            AuthRegistrationService authRegistrationService,
            SessionService sessionService,
            UserRepository userRepository) {
        this.authRegistrationService = authRegistrationService;
        this.sessionService = sessionService;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    ResponseEntity<?> register(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        String email = SessionService.normalizeEmail(String.valueOf(body.getOrDefault("email", "")));
        String password = String.valueOf(body.getOrDefault("password", ""));
        String name = body.get("name") instanceof String s ? s : null;

        if (!SessionService.isValidEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("error", "请输入有效邮箱地址"));
        }
        if (!SessionService.isValidPassword(password)) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码至少需要 8 位"));
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "该邮箱已被注册"));
        }

        Optional<UserEntity> userOpt = authRegistrationService.register(email, password, name);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "该邮箱已被注册"));
        }
        UserEntity user = userOpt.get();
        SessionService.SessionToken session = authRegistrationService.createSession(user.getId());
        sessionService.applySessionCookie(response, session.token(), session.expiresAt());
        return ResponseEntity.ok(Map.of("user", userResponse(user)));
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        String email = SessionService.normalizeEmail(String.valueOf(body.getOrDefault("email", "")));
        String password = String.valueOf(body.getOrDefault("password", ""));

        if (!SessionService.isValidEmail(email) || password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请输入邮箱和密码"));
        }

        Optional<UserEntity> userOpt = authRegistrationService.authenticate(email, password);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "邮箱或密码错误"));
        }
        UserEntity user = userOpt.get();
        SessionService.SessionToken session = authRegistrationService.createSession(user.getId());
        sessionService.applySessionCookie(response, session.token(), session.expiresAt());
        return ResponseEntity.ok(Map.of("user", userResponse(user)));
    }

    @PostMapping("/logout")
    ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        sessionService.destroySession(request);
        sessionService.clearSessionCookie(response);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/me")
    ResponseEntity<?> me(HttpServletRequest request) {
        var user = sessionService.getCurrentUser(request).orElse(null);
        return ResponseEntity.ok(Map.of("user", user));
    }

    private static Map<String, Object> userResponse(UserEntity user) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("email", user.getEmail());
        map.put("name", user.getName());
        map.put("avatarUrl", user.getAvatarUrl());
        return map;
    }
}
