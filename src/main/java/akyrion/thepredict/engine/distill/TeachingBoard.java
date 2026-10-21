package akyrion.thepredict.engine.distill;

import akyrion.thepredict.domain.distill.ChoiceStatDelta;
import akyrion.thepredict.domain.distill.LabelRule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Teaching snapshots, one per mob (M29.1, errata E29-4 applied). A Jev answer writes nothing. A closed
 * credit window emits the label. At most {@value #MAX_SNAPSHOTS} mobs are held (rule A5): a mob that captured and
 * never closed a window is dropped, oldest note first, and {@link #release(String)} drops a mob whose session ends.
 */
public final class TeachingBoard {
    /** Snapshots held at once. A snapshot that never closes cannot grow the board past this. */
    public static final int MAX_SNAPSHOTS = 1024;
    /** A board that keeps nothing and writes nothing, in place of a missing board (AE-12, C4). */
    public static final TeachingBoard NONE = new TeachingBoard(true);

    private final LinkedHashMap<String, Snap> slots = new LinkedHashMap<>();
    private final boolean inert;
    private int filtered;
    private Consumer<List<ChoiceStatDelta>> sink = rows -> {
    };

    /**
     * Starts empty. A capture notes into it only when collection is on.
     */
    public TeachingBoard() {
        this(false);
    }

    private TeachingBoard(boolean inert) {
        this.inert = inert;
    }

    /**
     * Whether this board keeps the snapshots of the captures; {@link #NONE} keeps none, so a capture or an answer
     * skips the work of noting, as it did without a board.
     *
     * @return {@code false} only for {@link #NONE}
     */
    public boolean keeps() {
        return !inert;
    }

    /**
     * Where closed windows send their rows.
     *
     * @param consumer the aggregate sink
     */
    public void useSink(Consumer<List<ChoiceStatDelta>> consumer) {
        if (!inert) {
            sink = consumer;
        }
    }

    /**
     * Records one mob. Another mob's snapshot stays.
     *
     * @param mob mob id
     * @param profileId profile id
     * @param regimeKey regime key
     * @param contextKey fine context
     * @param modelName teacher model
     * @param tactics offered tactics
     * @param values usable estimates
     * @param facts pattern facts on the target
     * @param none whether the effective pattern is none
     */
    public void note(String mob, String profileId, String regimeKey, String contextKey, String modelName,
                     List<String> tactics, List<LabelRule.Estimate> values, int facts, boolean none) {
        if (inert) {
            return;
        }
        slots.remove(mob);
        slots.put(mob, new Snap(profileId, regimeKey, contextKey, modelName, List.copyOf(tactics),
                List.copyOf(values), facts, none));
        if (slots.size() > MAX_SNAPSHOTS) {
            var eldest = slots.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
    }

    /**
     * Drops one mob's snapshot without a row. The session that owned it ended, so no window will close it.
     *
     * @param mob mob id
     */
    public void release(String mob) {
        slots.remove(mob);
    }

    /**
     * Snapshots held now.
     *
     * @return the count, at most {@value #MAX_SNAPSHOTS}
     */
    int snapshots() {
        return slots.size();
    }

    /**
     * Whether any capture was noted and not yet closed.
     *
     * @return the flag
     */
    public boolean noted() {
        return !slots.isEmpty();
    }

    /**
     * Whether one mob still holds a snapshot.
     *
     * @param mob mob id
     * @return the flag
     */
    public boolean noted(String mob) {
        return slots.containsKey(mob);
    }

    /**
     * Rows a Jev or cache answer would have written. Always empty, and the snapshot stays.
     *
     * @param live whether the answer came from Jev or the cache
     * @return no rows
     */
    public List<ChoiceStatDelta> jev(boolean live) {
        return List.of();
    }

    /**
     * Emits one mob's closed window and clears only that mob.
     *
     * @param mob mob id
     * @param epochMs wall time supplied by the adapter
     * @return the rows
     */
    public List<ChoiceStatDelta> closeMob(String mob, long epochMs) {
        var snap = slots.remove(mob);
        if (snap == null) {
            return List.of();
        }
        var rows = ChoiceIntake.rows(snap.profile, snap.regime, snap.context, snap.model, snap.offered, snap.estimates,
                snap.facts, snap.none, epochMs);
        if (rows.isEmpty() && snap.facts > 0 && snap.none && !snap.offered.isEmpty()) {
            filtered++;
        }
        if (!rows.isEmpty()) {
            sink.accept(rows);
        }
        return rows;
    }

    /**
     * Emits every open snapshot. Encounter close does not use this for labels.
     *
     * @param epochMs wall time supplied by the adapter
     * @return the rows
     */
    public List<ChoiceStatDelta> close(long epochMs) {
        var rows = new java.util.ArrayList<ChoiceStatDelta>();
        for (var mob : List.copyOf(slots.keySet())) {
            rows.addAll(closeMob(mob, epochMs));
        }
        return List.copyOf(rows);
    }

    /**
     * How many captures the pattern filter dropped.
     *
     * @return the count
     */
    public int filtered() {
        return filtered;
    }

    /**
     * The label of one mob, if its snapshot still holds one.
     *
     * @param mob mob id
     * @return the tactic
     */
    public Optional<String> label(String mob) {
        var snap = slots.get(mob);
        if (snap == null) {
            return Optional.empty();
        }
        return LabelRule.label(snap.estimates);
    }

    private record Snap(String profile, String regime, String context, String model, List<String> offered,
                        List<LabelRule.Estimate> estimates, int facts, boolean none) {
    }
}
