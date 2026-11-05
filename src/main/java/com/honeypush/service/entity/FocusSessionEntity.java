package com.honeypush.service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "focus_sessions")
public class FocusSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "officer_id", nullable = false, length = 32)
    private String officerId = "yuri";

    @Column(name = "task_id")
    private Integer taskId;

    @Column(nullable = false, length = 32)
    private String outcome = "completed";

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes = 25;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "coins_earned", nullable = false)
    private Integer coinsEarned = 15;

    @Column(name = "distraction_count", nullable = false)
    private Integer distractionCount = 0;

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

    public String getOfficerId() {
        return officerId;
    }

    public void setOfficerId(String officerId) {
        this.officerId = officerId;
    }

    public Integer getTaskId() {
        return taskId;
    }

    public void setTaskId(Integer taskId) {
        this.taskId = taskId;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getCoinsEarned() {
        return coinsEarned;
    }

    public void setCoinsEarned(Integer coinsEarned) {
        this.coinsEarned = coinsEarned;
    }

    public Integer getDistractionCount() {
        return distractionCount;
    }

    public void setDistractionCount(Integer distractionCount) {
        this.distractionCount = distractionCount;
    }
}
