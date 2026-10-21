package akyrion.thepredict.adapter.storage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which day folders to delete (M21.3). Pure: it does not touch the disk. The current day is never deleted. If that
 * day alone exceeds the cap, events pause for size and aggregates continue.
 */
public final class RetentionPolicy {
    private RetentionPolicy() {
    }

    /**
     * One day and its bytes.
     *
     * @param day {@code yyyy-MM-dd}
     * @param bytes size of the folder
     */
    public record DaySize(String day, long bytes) {
    }

    /**
     * The plan.
     *
     * @param delete days to delete, oldest first
     * @param pausedSize whether today alone exceeds the cap
     */
    public record Plan(List<String> delete, boolean pausedSize) {
    }

    /**
     * Plans the deletion.
     *
     * @param days known days
     * @param today the UTC day that must stay
     * @param retentionDays days kept, counting back from today
     * @param maxBytes the cap
     * @return the plan
     */
    public static Plan plan(List<DaySize> days, String today, int retentionDays, long maxBytes) {
        var ordered = new ArrayList<>(days);
        ordered.sort(Comparator.comparing(DaySize::day));
        var cutoff = LocalDate.parse(today).minusDays(retentionDays).toString();
        var delete = new ArrayList<String>();
        var kept = new ArrayList<DaySize>();
        for (var day : ordered) {
            if (day.day().compareTo(today) < 0 && day.day().compareTo(cutoff) < 0) {
                delete.add(day.day());
            } else {
                kept.add(day);
            }
        }
        var total = kept.stream().mapToLong(DaySize::bytes).sum();
        var index = 0;
        while (total > maxBytes && index < kept.size()) {
            var day = kept.get(index++);
            if (day.day().equals(today)) {
                break;
            }
            delete.add(day.day());
            total -= day.bytes();
        }
        var todayBytes = kept.stream().filter(day -> day.day().equals(today)).mapToLong(DaySize::bytes).sum();
        return new Plan(List.copyOf(delete), todayBytes > maxBytes);
    }
}
