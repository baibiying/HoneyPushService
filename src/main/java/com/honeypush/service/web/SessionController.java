package com.honeypush.service.web;

import com.honeypush.service.service.FocusSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final FocusSessionService focusSessionService;

    public SessionController(FocusSessionService focusSessionService) {
        this.focusSessionService = focusSessionService;
    }

    @GetMapping
    ResponseEntity<?> list() {
        String userId = CurrentUser.require().id();
        return ResponseEntity.ok(focusSessionService.listRecent(userId));
    }

    @PostMapping
    ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String userId = CurrentUser.require().id();
        String officerId = body.get("officerId") instanceof String s ? s : "yuri";
        int distractionCount = body.get("distractionCount") instanceof Number n ? n.intValue() : 0;
        String outcome = "failed".equals(body.get("outcome")) ? "failed" : "completed";
        Integer taskId = body.get("taskId") instanceof Number n && Double.isFinite(n.doubleValue()) ? n.intValue() : null;
        int durationMinutes = body.get("durationMinutes") instanceof Number n && n.doubleValue() > 0
                ? (int) Math.round(n.doubleValue())
                : 25;
        Integer coinsEarned = body.get("coinsEarned") instanceof Number n && Double.isFinite(n.doubleValue())
                ? (int) Math.round(n.doubleValue())
                : null;

        var result = focusSessionService.createSession(
                userId, officerId, distractionCount, outcome, taskId, durationMinutes, coinsEarned);
        return ResponseEntity.ok(Map.of("session", result.session(), "stats", result.stats()));
    }
}
