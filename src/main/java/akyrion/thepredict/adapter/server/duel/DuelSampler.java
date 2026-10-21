package akyrion.thepredict.adapter.server.duel;

import akyrion.thepredict.engine.IntentBoard;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The samples of a duel recording (M18.4): every {@value DuelSummary#SAMPLE_TICKS} ticks one row of
 * {@code samples.csv} per followed duelist, with the control level, tactic, movement and confidence the
 * {@link IntentBoard} publishes for a ThePredict duelist; the shield rows of {@code defense.csv}; and the retreats of
 * {@code retreats.csv}: {@code INTENT} for an intent with {@code RETREAT}, closed {@value #RETREAT_WINDOW_TICKS} ticks
 * later, and {@code MOTION} for a classic duelist that moved more than {@value #MOTION_RETREAT_BLOCKS} blocks away from
 * its rival within {@value #RETREAT_WINDOW_TICKS} ticks while the rival approached. Package-private; server thread only;
 * a history of at most 9 samples per followed duelist.
 */
final class DuelSampler {
    /** Ticks a retreat is measured over. */
    static final int RETREAT_WINDOW_TICKS = 40;
    /** Distance a classic duelist must move away from its rival to count as a retreat. */
    static final double MOTION_RETREAT_BLOCKS = 4.0;
    private static final int HISTORY = RETREAT_WINDOW_TICKS / DuelSummary.SAMPLE_TICKS + 1;

    private record Snapshot(long tick, double x, double z, double rivalX, double rivalZ, double distance) {
    }

    private record Pending(long tick, OptionalDouble distance) {
    }

    /** What the sampler remembers of one duelist. */
    private static final class Memory {
        private boolean raised;
        private boolean blocking;
        private final ArrayDeque<Snapshot> history = new ArrayDeque<>();
        private Optional<Pending> retreat = Optional.empty();
        private OptionalDouble distance = OptionalDouble.empty();
    }

    private final DuelRounds rounds;
    private final IntentBoard board;
    private final BiConsumer<DuelFile, String> rows;
    private final DuelSummary summary;
    private final Map<UUID, Memory> memories = new HashMap<>();

    DuelSampler(DuelRounds rounds, IntentBoard board, BiConsumer<DuelFile, String> rows, DuelSummary summary) {
        this.rounds = Objects.requireNonNull(rounds, "rounds");
        this.board = Objects.requireNonNull(board, "board");
        this.rows = Objects.requireNonNull(rows, "rows");
        this.summary = Objects.requireNonNull(summary, "summary");
    }

    /** Writes the rows of one sample of the followed duelists among {@code readings}. */
    void sample(List<DuelistReading> readings, long tick) {
        for (var reading : readings) {
            rounds.duelist(reading.id()).ifPresent(duelist -> sample(reading, duelist,
                    rival(readings, reading, duelist.side()), tick));
        }
    }

    /**
     * Opens an {@code INTENT} retreat of a followed duelist unless one is open. A mob that is not followed leaves no
     * memory: {@link #forget} only runs for followed duelists, so the map would otherwise grow for the whole recording
     * (A5).
     */
    void intentRetreat(UUID mob, long tick) {
        if (rounds.duelist(mob).isEmpty()) {
            return;
        }
        var memory = memories.computeIfAbsent(mob, key -> new Memory());
        if (memory.retreat.isEmpty()) {
            memory.retreat = Optional.of(new Pending(tick, memory.distance));
        }
    }

    /** Forgets a duelist that died or was removed; an open retreat is written with the last distance. */
    void forget(UUID mob, long tick) {
        var memory = memories.remove(mob);
        if (memory != null) {
            memory.retreat.ifPresent(pending -> retreat(mob, "INTENT", pending.distance(), memory.distance, tick));
        }
    }

    private void sample(DuelistReading reading, DuelRounds.Duelist duelist, Optional<DuelistReading> rival,
                        long tick) {
        var memory = memories.computeIfAbsent(reading.id(), key -> new Memory());
        memory.distance = rival.map(other -> OptionalDouble.of(reading.distanceTo(other)))
                .orElseGet(OptionalDouble::empty);
        var tp = duelist.kind() == DuelRounds.Kind.TP;
        var view = board.current(reading.id());
        rows.accept(DuelFile.SAMPLES, DuelRows.csv(tick, rounds.round(), rounds.arm(), reading.id(),
                duelist.side().text(), duelist.kind().text(), reading.healthRatio(), reading.absorption(), reading.x(),
                reading.y(), reading.z(), reading.yaw(), memory.distance, rival.map(reading::gapTo),
                reading.target(), tp ? view.level() : "", tp ? view.tactic().value() : "", tp ? view.movement() : "",
                tp ? (double) view.confidence() : Double.NaN, reading.raisedShield(), reading.blocking(),
                reading.activeItem()));
        if (tp && rounds.running()) {
            summary.level(rounds.arm(), view.level());
        }
        shields(reading, memory, tick);
        rival.ifPresent(other -> motion(reading, other, memory, duelist.kind(), tick));
        memory.retreat.filter(pending -> tick - pending.tick() >= RETREAT_WINDOW_TICKS).ifPresent(pending -> {
            memory.retreat = Optional.empty();
            retreat(reading.id(), "INTENT", pending.distance(), memory.distance, tick);
        });
    }

    private void shields(DuelistReading reading, Memory memory, long tick) {
        if (reading.raisedShield() && !memory.raised) {
            rows.accept(DuelFile.DEFENSE, DuelRows.csv(tick, rounds.round(), rounds.arm(), reading.id(),
                    "SHIELD_RAISED"));
        }
        if (reading.blocking() && !memory.blocking) {
            rows.accept(DuelFile.DEFENSE, DuelRows.csv(tick, rounds.round(), rounds.arm(), reading.id(),
                    "SHIELD_BLOCKING"));
        }
        memory.raised = reading.raisedShield();
        memory.blocking = reading.blocking();
    }

    /** A classic duelist that moved away from an approaching rival over the window counts as a retreat. */
    private void motion(DuelistReading reading, DuelistReading rival, Memory memory, DuelRounds.Kind kind, long tick) {
        var history = memory.history;
        history.addLast(new Snapshot(tick, reading.x(), reading.z(), rival.x(), rival.z(), reading.distanceTo(rival)));
        while (history.size() > HISTORY) {
            history.removeFirst();
        }
        var then = history.peekFirst();
        if (kind != DuelRounds.Kind.CLASSIC || then == null || tick - then.tick() < RETREAT_WINDOW_TICKS) {
            return;
        }
        var awayX = then.x() - then.rivalX();
        var awayZ = then.z() - then.rivalZ();
        var length = Math.hypot(awayX, awayZ);
        if (length == 0.0) {
            return;
        }
        var moved = ((reading.x() - then.x()) * awayX + (reading.z() - then.z()) * awayZ) / length;
        var approached = ((rival.x() - then.rivalX()) * awayX + (rival.z() - then.rivalZ()) * awayZ) / length;
        if (moved > MOTION_RETREAT_BLOCKS && approached > 0.0) {
            history.clear();
            retreat(reading.id(), "MOTION", OptionalDouble.of(then.distance()), memory.distance, tick);
        }
    }

    private void retreat(UUID mob, String source, OptionalDouble before, OptionalDouble after, long tick) {
        rows.accept(DuelFile.RETREATS, DuelRows.csv(tick, rounds.round(), rounds.arm(), mob, source, before, after));
        summary.retreat(rounds.arm(), source);
    }

    /** Duelists with a memory, for the tests of the cap. */
    int remembered() {
        return memories.size();
    }

    /** The nearest followed duelist of the other side. */
    private Optional<DuelistReading> rival(List<DuelistReading> readings, DuelistReading self, DuelRounds.Side side) {
        DuelistReading nearest = null;
        var nearestDistance = Double.POSITIVE_INFINITY;
        for (var reading : readings) {
            var other = rounds.duelist(reading.id());
            if (other.isPresent() && other.get().side() != side && self.distanceTo(reading) < nearestDistance) {
                nearest = reading;
                nearestDistance = self.distanceTo(reading);
            }
        }
        return Optional.ofNullable(nearest);
    }
}
