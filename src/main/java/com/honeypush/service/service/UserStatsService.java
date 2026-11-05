package com.honeypush.service.service;

import com.honeypush.service.entity.UserStatsEntity;
import com.honeypush.service.repository.UserStatsRepository;
import com.honeypush.service.web.dto.UserStatsDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class UserStatsService {

    private final UserStatsRepository userStatsRepository;

    public UserStatsService(UserStatsRepository userStatsRepository) {
        this.userStatsRepository = userStatsRepository;
    }

    @Transactional(readOnly = true)
    public Optional<UserStatsDto> get(String userId) {
        return userStatsRepository.findByUserId(userId).map(UserStatsDto::from);
    }

    @Transactional
    public UserStatsDto upsert(String userId, int deltaCoins) {
        Instant now = Instant.now();
        Optional<UserStatsEntity> existingOpt = userStatsRepository.findByUserId(userId);
        if (existingOpt.isEmpty()) {
            UserStatsEntity created = new UserStatsEntity();
            created.setUserId(userId);
            created.setTotalCoins(deltaCoins);
            created.setConsecutiveDays(1);
            created.setTotalSessions(1);
            created.setLastSessionDate(now);
            created.setUnlockedBadges(List.of("exam_badge"));
            created.setUpdatedAt(now);
            return UserStatsDto.from(userStatsRepository.save(created));
        }

        UserStatsEntity existing = existingOpt.get();
        int consecutive = existing.getConsecutiveDays();
        Instant lastDate = existing.getLastSessionDate();
        if (lastDate != null) {
            long dayDiff = (now.toEpochMilli() - lastDate.toEpochMilli()) / (1000 * 60 * 60 * 24);
            if (dayDiff == 1) {
                consecutive += 1;
            } else if (dayDiff > 1) {
                consecutive = 1;
            }
        }

        existing.setTotalCoins(existing.getTotalCoins() + deltaCoins);
        existing.setConsecutiveDays(consecutive);
        existing.setTotalSessions(existing.getTotalSessions() + 1);
        existing.setLastSessionDate(now);
        existing.setUpdatedAt(now);
        return UserStatsDto.from(userStatsRepository.save(existing));
    }
}
