package com.honeypush.service.service;

import com.honeypush.service.entity.FocusSessionEntity;
import com.honeypush.service.repository.FocusSessionRepository;
import com.honeypush.service.web.dto.FocusSessionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class FocusSessionService {

    private final FocusSessionRepository focusSessionRepository;
    private final UserStatsService userStatsService;

    public FocusSessionService(FocusSessionRepository focusSessionRepository, UserStatsService userStatsService) {
        this.focusSessionRepository = focusSessionRepository;
        this.userStatsService = userStatsService;
    }

    @Transactional(readOnly = true)
    public List<FocusSessionDto> listRecent(String userId) {
        return focusSessionRepository.findTop30ByUserIdOrderByCompletedAtDesc(userId).stream()
                .map(FocusSessionDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FocusSessionDto> listAll(String userId) {
        return focusSessionRepository.findByUserIdOrderByCompletedAtDesc(userId).stream()
                .map(FocusSessionDto::from)
                .toList();
    }

    @Transactional
    public CreateSessionResult createSession(
            String userId,
            String officerId,
            int distractionCount,
            String outcome,
            Integer taskId,
            int durationMinutes,
            Integer coinsEarned) {
        int earned = "failed".equals(outcome)
                ? 0
                : coinsEarned != null ? Math.max(0, coinsEarned) : 15;

        FocusSessionEntity session = new FocusSessionEntity();
        session.setUserId(userId);
        session.setOfficerId(officerId);
        session.setTaskId(taskId);
        session.setOutcome(outcome);
        session.setDistractionCount(distractionCount);
        session.setDurationMinutes(durationMinutes);
        session.setCoinsEarned(earned);
        session.setCompletedAt(Instant.now());
        FocusSessionDto saved = FocusSessionDto.from(focusSessionRepository.save(session));

        var stats = "completed".equals(outcome)
                ? userStatsService.upsert(userId, saved.coinsEarned())
                : userStatsService.upsert(userId, 0);
        return new CreateSessionResult(saved, stats);
    }

    public record CreateSessionResult(FocusSessionDto session, com.honeypush.service.web.dto.UserStatsDto stats) {}
}
