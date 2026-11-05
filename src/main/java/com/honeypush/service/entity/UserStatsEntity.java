package com.honeypush.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "user_stats")
public class UserStatsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "total_coins", nullable = false)
    private Integer totalCoins = 0;

    @Column(name = "consecutive_days", nullable = false)
    private Integer consecutiveDays = 0;

    @Column(name = "total_sessions", nullable = false)
    private Integer totalSessions = 0;

    @Column(name = "last_session_date")
    private Instant lastSessionDate;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "unlocked_badges", columnDefinition = "text[]")
    private List<String> unlockedBadges;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getTotalCoins() {
        return totalCoins;
    }

    public void setTotalCoins(Integer totalCoins) {
        this.totalCoins = totalCoins;
    }

    public Integer getConsecutiveDays() {
        return consecutiveDays;
    }

    public void setConsecutiveDays(Integer consecutiveDays) {
        this.consecutiveDays = consecutiveDays;
    }

    public Integer getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(Integer totalSessions) {
        this.totalSessions = totalSessions;
    }

    public Instant getLastSessionDate() {
        return lastSessionDate;
    }

    public void setLastSessionDate(Instant lastSessionDate) {
        this.lastSessionDate = lastSessionDate;
    }

    public List<String> getUnlockedBadges() {
        return unlockedBadges;
    }

    public void setUnlockedBadges(List<String> unlockedBadges) {
        this.unlockedBadges = unlockedBadges;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
