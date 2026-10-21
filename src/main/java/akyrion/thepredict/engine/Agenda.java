package akyrion.thepredict.engine;

import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * Decides which encounters evaluate on each tick and which sessions get a cheap probe, with bounded work and a
 * fair share between worlds (M06.5, M09.5). Server thread only.
 *
 * <p>Each world has a heap of encounters ordered by due tick, with lazy deletion: rescheduling or removing an
 * encounter makes its older entries stale. {@link #due} visits the worlds round-robin, one encounter per world
 * per round. Context pending moves the due tick to {@code max(lastDecision + minimumInterval, now)}, so it goes
 * ahead of the heartbeat without breaking the minimum interval; many marks produce one move. An encounter with
 * a request in flight is never returned. A returned encounter stays due until {@link #decided} or
 * {@link #schedule} moves it, so an encounter the caller skips is offered again on the next tick.
 */
public final class Agenda {
    private static final Comparator<Entry> ORDER = Comparator.comparingLong(Entry::dueTick)
            .thenComparing(entry -> !entry.pending())
            .thenComparingLong(entry -> entry.encounter().id().value());

    private final Map<UUID, PriorityQueue<Entry>> worlds = new HashMap<>();
    private final ArrayDeque<UUID> rotation = new ArrayDeque<>();
    private final ProbeRing probes = new ProbeRing();
    private final ArrayList<Entry> held = new ArrayList<>();

    /** Creates an empty agenda. */
    public Agenda() {
    }

    /**
     * Schedules the next evaluation of an encounter, replacing the previous one.
     *
     * @param encounter open encounter
     * @param dueTick first tick on which it may evaluate
     */
    public void schedule(Encounter encounter, long dueTick) {
        Objects.requireNonNull(encounter, "encounter");
        if (encounter.isClosed()) {
            return;
        }
        encounter.schedule().nextDecisionTick(dueTick);
        var entry = new Entry(encounter, dueTick, encounter.schedule().nextVersion(), encounter.contextPending());
        var queue = worlds.computeIfAbsent(encounter.worldId(), world -> {
            rotation.addLast(world);
            return new PriorityQueue<>(ORDER);
        });
        queue.add(entry);
    }

    /**
     * Why an encounter was marked pending (M20.6, errata E20-7). {@link #CONTEXT} is the mark of M09.
     * {@link #SIGNATURE} is a situation change under {@code REACTIVE}; it condenses the same way.
     */
    public enum PendingCause {
        /** A signal, a probe or an event. */
        CONTEXT,
        /** The situation signature changed. */
        SIGNATURE,
        /** The group plan died and the next capture should ask {@code g0} (M25, B5). */
        PLAN_LOST
    }

    /**
     * Marks that something relevant happened (a {@code predictordecide} signal or an M07 event). The encounter
     * evaluates as soon as its minimum interval since the last decision allows; repeated marks are condensed.
     *
     * @param encounter open encounter
     * @param tick current server tick
     */
    public void markContextPending(Encounter encounter, long tick) {
        markContextPending(encounter, tick, PendingCause.CONTEXT);
    }

    /**
     * Marks the encounter pending and remembers the cause. A mark that is already pending is condensed and keeps
     * the first cause, except {@link PendingCause#PLAN_LOST}, which replaces it: the plan died and the next
     * capture has to know.
     *
     * @param encounter open encounter
     * @param tick current server tick
     * @param cause why the situation should be asked again
     */
    public void markContextPending(Encounter encounter, long tick, PendingCause cause) {
        if (encounter.isClosed()) {
            return;
        }
        if (encounter.contextPending()) {
            if (cause == PendingCause.PLAN_LOST) {
                encounter.pendingCause(PendingCause.PLAN_LOST);
            }
            return;
        }
        encounter.pendingCause(cause);
        encounter.schedule().contextPending(true);
        var last = encounter.schedule().lastDecisionTick();
        var spacing = pendingSpacing(encounter, tick);
        var earliest = last.isPresent() ? Math.max(tick, last.getAsLong() + spacing) : tick;
        schedule(encounter, Math.min(earliest, encounter.schedule().nextDecisionTick()));
    }

    /**
     * Records a decision: clears the pending context and schedules the next heartbeat, never before the minimum
     * interval.
     *
     * @param encounter open encounter
     * @param tick tick of the decision
     */
    public void decided(Encounter encounter, long tick) {
        encounter.schedule().lastDecisionTick(tick);
        encounter.schedule().contextPending(false);
        schedule(encounter, tick + decidedSpacing(encounter, tick));
    }

    /**
     * Spacing of a pending mark. Entropy uses {@code min(c(t), ReactiveInterval)}; an encounter whose members all
     * ask with {@code REACTIVE} uses {@code Timing.ReactiveInterval} (M20.7); otherwise the minimum decision
     * interval, which is what lets a mark move ahead of the heartbeat. The heartbeat itself never changes: the
     * reactive interval only shortens the wait to ask again after something happened.
     *
     * @param encounter open encounter
     * @param tick current tick
     * @return ticks to add to the last decision
     */
    private static int pendingSpacing(Encounter encounter, long tick) {
        var entropy = entropySpacing(encounter, tick);
        if (entropy >= 0) {
            return entropy;
        }
        var reactive = encounter.reactiveIntervalTicks();
        return reactive > 0 ? reactive : encounter.minimumDecisionIntervalTicks();
    }

    /**
     * Spacing after a decision. Entropy uses the same gap; otherwise the heartbeat, never under the minimum.
     *
     * @param encounter open encounter
     * @param tick current tick
     * @return ticks until the next heartbeat
     */
    private static int decidedSpacing(Encounter encounter, long tick) {
        var entropy = entropySpacing(encounter, tick);
        if (entropy >= 0) {
            return entropy;
        }
        return Math.max(encounter.heartbeatTicks(), encounter.minimumDecisionIntervalTicks());
    }

    /** Entropy question gap, or {@code -1} when the encounter is not in entropy. */
    private static int entropySpacing(Encounter encounter, long tick) {
        var entropy = encounter.entropyGap(tick);
        if (entropy.isEmpty()) {
            return -1;
        }
        return Math.max(1, entropy.getAsInt());
    }

    /**
     * Forgets an encounter; its heap entries become stale.
     *
     * @param encounter encounter to forget
     */
    public void remove(Encounter encounter) {
        encounter.schedule().nextVersion();
    }

    /**
     * Returns the encounters that should evaluate now, one per world per round, at most {@code maximum}.
     *
     * @param tick current server tick
     * @param maximum limit per tick ({@code decision.maxEncounterEvaluationsPerTick}), {@code >= 0}
     * @return due encounters without a request in flight, in the order to evaluate them
     */
    public List<EncounterId> due(long tick, int maximum) {
        requireLimit(maximum);
        var result = new ArrayList<EncounterId>(Math.min(maximum, 16));
        held.clear();
        var progress = true;
        while (result.size() < maximum && progress && !rotation.isEmpty()) {
            progress = false;
            for (var turn = rotation.size(); turn > 0 && result.size() < maximum; turn--) {
                var world = rotation.pollFirst();
                rotation.addLast(world);
                var entry = nextDue(worlds.get(world), tick, held);
                if (entry != null) {
                    result.add(entry.encounter().id());
                    held.add(entry);
                    progress = true;
                }
            }
        }
        for (var entry : held) {
            worlds.get(entry.encounter().worldId()).add(entry);
        }
        held.clear();
        dropEmptyWorlds();
        return result;
    }

    /**
     * Tells whether some open encounter without a request in flight is due, without polling or rotating anything, so a
     * tick that skips its decisions only counts as deferred when a decision was actually waiting (M13).
     *
     * @param tick current server tick
     * @return {@code true} if {@link #due} would return an encounter on this tick
     */
    public boolean hasDue(long tick) {
        for (var queue : worlds.values()) {
            for (var entry : queue) {
                var encounter = entry.encounter();
                if (entry.dueTick() <= tick && !encounter.isClosed()
                        && entry.version() == encounter.schedule().version()
                        && !encounter.schedule().requestInFlight()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the sessions whose cheap probe falls on this tick, those with
     * {@code (uuid.hashCode() + tick) % 2 == 0}, at most {@code maximum}; each gets its next probe two ticks later.
     *
     * @param tick current server tick
     * @param maximum limit per tick ({@code decision.maxProbesPerTick}), {@code >= 0}
     * @return keys of the sessions to probe
     */
    public List<MobKey> dueProbes(long tick, int maximum) {
        var keys = new ArrayList<MobKey>(Math.min(maximum, probes.size()));
        visitProbes(tick, maximum, session -> keys.add(session.key()));
        return keys;
    }

    /**
     * Visits the sessions whose cheap probe falls on this tick, at most {@code maximum}, without building a list of
     * keys (rule C12); each gets its next probe two ticks later before the visitor sees it.
     *
     * @param tick current server tick
     * @param maximum limit per tick ({@code decision.maxProbesPerTick}), {@code >= 0}
     * @param visitor receives each due session
     * @return sessions visited
     */
    public int visitProbes(long tick, int maximum, java.util.function.Consumer<MobSession> visitor) {
        requireLimit(maximum);
        return probes.visit(tick, maximum, java.util.Objects.requireNonNull(visitor, "visitor"));
    }

    /** Adds an active session to the probe ring; called by {@link SessionRegistry}. */
    void addProbe(MobSession session) {
        probes.add(session);
    }

    /** Removes a session from the probe ring; called by {@link SessionRegistry}. */
    void removeProbe(UUID entityId) {
        probes.remove(entityId);
    }

    /** Pops the next due, current entry of one world; entries in flight are held aside. */
    private static Entry nextDue(PriorityQueue<Entry> queue, long tick, List<Entry> held) {
        while (!queue.isEmpty()) {
            var entry = queue.peek();
            var encounter = entry.encounter();
            if (encounter.isClosed() || entry.version() != encounter.schedule().version()) {
                queue.poll();
                continue;
            }
            if (entry.dueTick() > tick) {
                return null;
            }
            queue.poll();
            if (!encounter.schedule().requestInFlight()) {
                return entry;
            }
            held.add(entry);
        }
        return null;
    }

    /** Removes worlds without entries from the rotation. */
    private void dropEmptyWorlds() {
        rotation.removeIf(world -> {
            if (worlds.get(world).isEmpty()) {
                worlds.remove(world);
                return true;
            }
            return false;
        });
    }

    private static void requireLimit(int maximum) {
        if (maximum < 0) {
            throw new IllegalArgumentException("maximum must be >= 0, was " + maximum);
        }
    }

    /** One scheduled evaluation; stale when its version is not the encounter's current one. */
    private record Entry(Encounter encounter, long dueTick, long version, boolean pending) {
    }
}
