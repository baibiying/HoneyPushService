package com.honeypush.service.ai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SchedulePriority {
    private static final Set<String> VALID = Set.of(
            "import-urgent", "import-noturgent", "notimport-urgent", "notimport-noturgent");

    private static final Map<String, Integer> PRIORITY = Map.of(
            "import-urgent", 1,
            "notimport-urgent", 2,
            "import-noturgent", 3,
            "notimport-noturgent", 4);

    private SchedulePriority() {}

    public record SchedulableTaskRef(int id, String category, int order, int durationMinutes) {}

    public static String normalizeQuadrantCategory(String category) {
        return VALID.contains(category) ? category : "import-noturgent";
    }

    public static int getQuadrantSchedulePriority(String category) {
        return PRIORITY.getOrDefault(normalizeQuadrantCategory(category), 3);
    }

    public static int compareTasksForSchedule(
            SchedulableTaskRef a, SchedulableTaskRef b, Map<Integer, Instant> deadlinesById) {
        int quadrantDiff = getQuadrantSchedulePriority(a.category()) - getQuadrantSchedulePriority(b.category());
        if (quadrantDiff != 0) {
            return quadrantDiff;
        }
        Instant da = deadlinesById.get(a.id());
        Instant db = deadlinesById.get(b.id());
        long deadlineA = da != null ? da.toEpochMilli() : Long.MAX_VALUE;
        long deadlineB = db != null ? db.toEpochMilli() : Long.MAX_VALUE;
        if (deadlineA != deadlineB) {
            return Long.compare(deadlineA, deadlineB);
        }
        if (a.order() != b.order()) {
            return Integer.compare(a.order(), b.order());
        }
        return Integer.compare(a.id(), b.id());
    }

    public static List<SchedulableTaskRef> rankTasksForSchedule(
            List<SchedulableTaskRef> items, Map<Integer, Instant> deadlinesById) {
        List<SchedulableTaskRef> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> compareTasksForSchedule(a, b, deadlinesById));
        List<SchedulableTaskRef> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            SchedulableTaskRef item = sorted.get(i);
            result.add(new SchedulableTaskRef(item.id(), item.category(), i + 1, item.durationMinutes()));
        }
        return result;
    }
}
