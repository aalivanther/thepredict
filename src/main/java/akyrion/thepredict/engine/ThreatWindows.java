package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EventType;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.ThreatPolicy;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Threat windows of one {@link Encounter} (01 §8, M11.3): the points each member's table gave each participant per
 * limited fact in the current window of {@link ThreatPolicy#WINDOW_TICKS} ticks, and the members whose target was
 * unreachable at the previous decision, with the capture that saw it. Owned by its encounter and dropped with it;
 * package-private; server thread only.
 *
 * <p>A window starts at the first gain of its key and a gain at {@code start + WINDOW_TICKS} opens the next one. At
 * most {@link #CAPACITY} windows exist: a new key first drops the expired windows and is refused, as limited, if the
 * encounter is still full. Unreachable marks are kept only for current members, one per member (rule A5).
 */
final class ThreatWindows {
    /** Facts that go through the limiter: assists, blocked hits, ranged harass and group assignments. */
    static final int LIMITED_FACTS = 4;
    /** Most windows of one encounter: members × participants × limited facts. */
    static final int CAPACITY = EncounterRegistry.MAX_MEMBERS * EncounterRegistry.MAX_PARTICIPANTS * LIMITED_FACTS;

    private final Map<WindowKey, Window> windows = new HashMap<>();
    private final Map<UUID, Mark> unreachable = new HashMap<>();

    /**
     * Admits as much of a gain as the window of that member table, participant and fact has left, and counts it.
     *
     * @return the admitted gain; {@code 0} when the window is spent or no window fits
     */
    double admit(MobKey mob, UUID participant, EventType fact, double change, double maxPerWindow, long tick) {
        var key = new WindowKey(Objects.requireNonNull(mob, "mob"), Objects.requireNonNull(participant, "participant"),
                Objects.requireNonNull(fact, "fact"));
        var window = windows.get(key);
        if (window == null || tick - window.startTick >= ThreatPolicy.WINDOW_TICKS) {
            if (window == null && !roomFor(tick)) {
                return 0.0;
            }
            window = new Window(tick);
            windows.put(key, window);
        }
        var admitted = ThreatPolicy.admitted(change, window.points, maxPerWindow);
        window.points += admitted;
        return admitted;
    }

    /**
     * Records that a member saw its target unreachable at this decision, with the facts of the capture that saw it.
     *
     * @return the tick of the previous decision if it saw the same target unreachable for the same generation, so the
     *         decay covers the time between them; empty on the first one
     */
    OptionalLong markUnreachable(MobKey mob, UUID target, MobFacts facts, long tick) {
        var previous = unreachable.put(mob.entityId(), new Mark(mob, Objects.requireNonNull(target, "target"),
                Objects.requireNonNull(facts, "facts"), tick));
        return previous != null && previous.mob().equals(mob) && previous.target().equals(target)
                ? OptionalLong.of(previous.tick()) : OptionalLong.empty();
    }

    /**
     * Tells whether the facts of a member are the very instance its mark was made with, that is, no capture read the
     * member since the previous decision: each capture stores a new {@code MobFacts} instance.
     */
    boolean sameCapture(UUID mobId, MobFacts facts) {
        var mark = unreachable.get(mobId);
        return mark != null && mark.facts() == facts;
    }

    /** Forgets the unreachable mark of a member whose target is reachable, unknown or changed. */
    void forgetUnreachable(UUID mobId) {
        unreachable.remove(mobId);
    }

    /** Keeps the unreachable marks of the current members of the encounter only. */
    void keepMembers(Encounter encounter) {
        unreachable.keySet().removeIf(mobId -> !encounter.isMember(mobId));
    }

    /**
     * Sums the points the current windows admitted to the table of one member, for {@code /thepredict inspect}
     * (M12.2).
     *
     * @return threat points ThePredict added to that table in the windows still open at {@code tick}
     */
    double pointsFor(MobKey mob, long tick) {
        var total = 0.0;
        for (var entry : windows.entrySet()) {
            if (entry.getKey().mob().equals(mob) && tick - entry.getValue().startTick < ThreatPolicy.WINDOW_TICKS) {
                total += entry.getValue().points;
            }
        }
        return total;
    }

    /** Counts the open windows, for tests of the bound. */
    int windowCount() {
        return windows.size();
    }

    /** Counts the unreachable marks, for tests of the bound. */
    int unreachableCount() {
        return unreachable.size();
    }

    /** Drops every window and mark; the encounter closed. */
    void clear() {
        windows.clear();
        unreachable.clear();
    }

    private boolean roomFor(long tick) {
        if (windows.size() < CAPACITY) {
            return true;
        }
        windows.values().removeIf(window -> tick - window.startTick >= ThreatPolicy.WINDOW_TICKS);
        return windows.size() < CAPACITY;
    }

    /** One member table, one participant and one limited fact. */
    private record WindowKey(MobKey mob, UUID participant, EventType fact) {
    }

    /** Points given in the window that started at {@code startTick}. */
    private static final class Window {
        private final long startTick;
        private double points;

        private Window(long startTick) {
            this.startTick = startTick;
        }
    }

    /** The target a member saw unreachable at the decision of {@code tick}, and the facts of that capture. */
    private record Mark(MobKey mob, UUID target, MobFacts facts, long tick) {
    }
}
