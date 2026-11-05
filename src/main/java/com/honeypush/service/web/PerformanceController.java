package com.honeypush.service.web;

import com.honeypush.service.service.FocusSessionService;
import com.honeypush.service.service.TaskPerformanceService;
import com.honeypush.service.service.TaskService;
import com.honeypush.service.service.UserStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/performance")
public class PerformanceController {

    private final TaskService taskService;
    private final FocusSessionService focusSessionService;
    private final UserStatsService userStatsService;
    private final TaskPerformanceService taskPerformanceService;

    public PerformanceController(
            TaskService taskService,
            FocusSessionService focusSessionService,
            UserStatsService userStatsService,
            TaskPerformanceService taskPerformanceService) {
        this.taskService = taskService;
        this.focusSessionService = focusSessionService;
        this.userStatsService = userStatsService;
        this.taskPerformanceService = taskPerformanceService;
    }

    @GetMapping
    ResponseEntity<?> get() {
        String userId = CurrentUser.require().id();
        var tasks = taskService.listTasks(userId);
        var sessions = focusSessionService.listAll(userId);
        int totalCoins = userStatsService.get(userId).map(s -> s.totalCoins()).orElse(0);
        return ResponseEntity.ok(taskPerformanceService.buildReport(tasks, sessions, totalCoins, Instant.now()));
    }
}
