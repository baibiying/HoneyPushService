package com.honeypush.service.service;

import com.honeypush.service.entity.ScheduledFocusSegmentEmbeddable;
import com.honeypush.service.entity.TaskEntity;
import com.honeypush.service.repository.TaskRepository;
import com.honeypush.service.web.dto.TaskDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<TaskDto> listTasks(String userId) {
        return taskRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(TaskDto::from)
                .toList();
    }

    @Transactional
    public TaskDto createTask(String userId, String text, String category, int durationMinutes, Instant deadline) {
        Instant now = Instant.now();
        TaskEntity task = new TaskEntity();
        task.setUserId(userId);
        task.setText(text);
        task.setCategory(category);
        task.setDurationMinutes(durationMinutes);
        task.setDeadline(deadline);
        task.setChecked(false);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return TaskDto.from(taskRepository.save(task));
    }

    @Transactional
    public Optional<TaskDto> updateTask(String userId, int id, TaskUpdate update) {
        Optional<TaskEntity> existing = taskRepository.findByIdAndUserId(id, userId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        TaskEntity task = existing.get();
        update.text().ifPresent(task::setText);
        update.checked().ifPresent(task::setChecked);
        update.category().ifPresent(task::setCategory);
        update.durationMinutes().ifPresent(task::setDurationMinutes);
        if (update.deadlineSet()) {
            task.setDeadline(update.deadline());
        }
        if (update.scheduledStartAtSet()) {
            task.setScheduledStartAt(update.scheduledStartAt());
        }
        if (update.scheduledEndAtSet()) {
            task.setScheduledEndAt(update.scheduledEndAt());
        }
        if (update.scheduledFocusSegmentsSet()) {
            task.setScheduledFocusSegments(update.scheduledFocusSegments());
        }
        task.setUpdatedAt(Instant.now());
        return Optional.of(TaskDto.from(taskRepository.save(task)));
    }

    @Transactional
    public boolean deleteTask(String userId, int id) {
        Optional<TaskEntity> existing = taskRepository.findByIdAndUserId(id, userId);
        if (existing.isEmpty()) {
            return false;
        }
        taskRepository.delete(existing.get());
        return true;
    }

    public record TaskUpdate(
            java.util.Optional<String> text,
            java.util.Optional<Boolean> checked,
            java.util.Optional<String> category,
            java.util.Optional<Integer> durationMinutes,
            Instant deadline,
            boolean deadlineSet,
            Instant scheduledStartAt,
            boolean scheduledStartAtSet,
            Instant scheduledEndAt,
            boolean scheduledEndAtSet,
            List<ScheduledFocusSegmentEmbeddable> scheduledFocusSegments,
            boolean scheduledFocusSegmentsSet) {

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private java.util.Optional<String> text = java.util.Optional.empty();
            private java.util.Optional<Boolean> checked = java.util.Optional.empty();
            private java.util.Optional<String> category = java.util.Optional.empty();
            private java.util.Optional<Integer> durationMinutes = java.util.Optional.empty();
            private Instant deadline;
            private boolean deadlineSet;
            private Instant scheduledStartAt;
            private boolean scheduledStartAtSet;
            private Instant scheduledEndAt;
            private boolean scheduledEndAtSet;
            private List<ScheduledFocusSegmentEmbeddable> scheduledFocusSegments;
            private boolean scheduledFocusSegmentsSet;

            public Builder text(String value) {
                this.text = java.util.Optional.of(value);
                return this;
            }

            public Builder checked(boolean value) {
                this.checked = java.util.Optional.of(value);
                return this;
            }

            public Builder category(String value) {
                this.category = java.util.Optional.of(value);
                return this;
            }

            public Builder durationMinutes(int value) {
                this.durationMinutes = java.util.Optional.of(value);
                return this;
            }

            public Builder deadline(Instant value) {
                this.deadline = value;
                this.deadlineSet = true;
                return this;
            }

            public Builder scheduledStartAt(Instant value) {
                this.scheduledStartAt = value;
                this.scheduledStartAtSet = true;
                return this;
            }

            public Builder scheduledEndAt(Instant value) {
                this.scheduledEndAt = value;
                this.scheduledEndAtSet = true;
                return this;
            }

            public Builder scheduledFocusSegments(List<ScheduledFocusSegmentEmbeddable> value) {
                this.scheduledFocusSegments = value;
                this.scheduledFocusSegmentsSet = true;
                return this;
            }

            public TaskUpdate build() {
                return new TaskUpdate(
                        text,
                        checked,
                        category,
                        durationMinutes,
                        deadline,
                        deadlineSet,
                        scheduledStartAt,
                        scheduledStartAtSet,
                        scheduledEndAt,
                        scheduledEndAtSet,
                        scheduledFocusSegments,
                        scheduledFocusSegmentsSet);
            }
        }
    }
}
