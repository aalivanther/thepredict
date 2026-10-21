package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DifficultyGlobals;
import akyrion.thepredict.domain.DiscardReason;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.GroupPlan;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Group step (M25). It runs after lifecycle and before the tick layer. One member, or mode off, leaves the cast
 * path untouched. A programming error in the assignment drops that encounter to fair for the tick, and an exception
 * while stepping one encounter drops only that encounter to fair: the others are still coordinated (rule C7).
 */
public final class GroupDirector implements GroupControl, GroupBriefSource {
    private final EncounterRegistry registry;
    private final ServerClock clock;
    private final DecisionMetrics metrics;
    private final GroupCaps caps;
    private final Agenda agenda;
    private final Map<EncounterId, GroupState> states = new LinkedHashMap<>();
    private final Map<UUID, Integer> replans = new HashMap<>();
    private CombatActions cues;
    private GroupAssignment assignment;
    private int disengageTicks = DifficultyGlobals.DEFAULT.disengageTicks();
    private int arbitrations;
    private boolean over;

    /**
     * Creates the director.
     *
     * @param registry open encounters
     * @param clock server clock
     * @param metrics group counters
     * @param caps global limits
     * @param agenda where a dead plan marks {@code PLAN_LOST}
     */
    public GroupDirector(EncounterRegistry registry, ServerClock clock, DecisionMetrics metrics, GroupCaps caps,
                         Agenda agenda) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.caps = Objects.requireNonNull(caps, "caps");
        this.agenda = Objects.requireNonNull(agenda, "agenda");
    }

    /**
     * Signals sent when a role or a plan changes. Absent until the plugin wires the cast port.
     *
     * @param actions the cue port
     */
    public void useCues(CombatActions actions) {
        cues = Objects.requireNonNull(actions, "actions");
    }

    /**
     * Threat writer for one assignment per shared table.
     *
     * @param assignment the writer
     */
    public void useAssignment(GroupAssignment assignment) {
        this.assignment = Objects.requireNonNull(assignment, "assignment");
    }

    /**
     * The difficulty section, so a group clock disengages after the same configured gap as the clock of one mob
     * ({@code difficulty.disengageTicks}, EFO-12). Until it is installed the delivered gap applies.
     *
     * @param globals the difficulty section in use
     */
    public void useDifficulty(DifficultyGlobals globals) {
        disengageTicks = Objects.requireNonNull(globals, "globals").disengageTicks();
    }

    /**
     * The brief the next capture of this encounter may attach. Empty when the encounter is not a full group.
     *
     * @param encounter the encounter
     * @return the brief, or {@link akyrion.thepredict.domain.GroupBrief#NONE}
     */
    public akyrion.thepredict.domain.GroupBrief brief(EncounterId encounter) {
        var state = states.get(encounter);
        return state == null ? akyrion.thepredict.domain.GroupBrief.NONE : state.brief();
    }

    /**
     * Plan in force, or null when the encounter has none.
     *
     * @param encounter the encounter
     * @return the plan
     */
    GroupPlan plan(EncounterId encounter) {
        var state = states.get(encounter);
        return state == null ? null : state.currentPlan();
    }

    /**
     * Plan epoch last settled, or 0 when the encounter has not been stepped.
     *
     * @param encounter the encounter
     * @return the epoch
     */
    long planEpoch(EncounterId encounter) {
        var state = states.get(encounter);
        return state == null ? 0L : state.planEpoch();
    }

    @Override
    public void step(long tick, long budgetNanos) {
        var start = clock.nanoTime();
        replans.clear();
        arbitrations = 0;
        over = false;
        try {
            for (var encounter : registry.open()) {
                if (encounter.isClosed()) {
                    continue;
                }
                var state = states.computeIfAbsent(encounter.id(), id -> new GroupState());
                try {
                    stepOne(encounter, state, tick, start, budgetNanos);
                } catch (RuntimeException exception) {
                    state.fallToFair();
                    metrics.add(DecisionMetrics.Counter.STEP_FAILURES, 1);
                }
                if (over) {
                    break;
                }
            }
        } finally {
            states.keySet().removeIf(id -> registry.find(id).isEmpty());
        }
    }

    /** Steps one encounter: roster, plan, arbitration, clock and publication, inside the shared budget. */
    private void stepOne(Encounter encounter, GroupState state, long tick, long start, long budgetNanos) {
        var refresh = refresh(encounter, state.sync(encounter, tick, caps), tick);
        if (state.settle(encounter, tick, caps, refresh)) {
            agenda.markContextPending(encounter, tick, Agenda.PendingCause.PLAN_LOST);
        }
        if (refresh && clock.nanoTime() - start > budgetNanos) {
            over = true;
        }
        if (state.takeAnswerRejected()) {
            metrics.increment(DiscardReason.GROUP_EPOCH_CHANGED);
        }
        if (state.arbitrate(tick, caps, (int) encounter.id().value())) {
            arbitrations++;
            if (arbitrations % 8 == 0 && clock.nanoTime() - start > budgetNanos) {
                over = true;
            }
        }
        state.fights.quiet(tick, disengageTicks);
        encounter.noteGroupMode(state.mode(caps));
        publish(encounter, state, tick, refresh);
        if (state.tells.takeExpired()) {
            metrics.add(DecisionMetrics.GroupCounter.LEADER_RESERVE_EXPIRED, 1);
        }
    }

    /**
     * Whether this encounter replans its roles now: when its roster changed or the replan period comes, unless its
     * world already used {@code maxReplansPerTick} or the budget ran out after another replan of that world.
     */
    private boolean refresh(Encounter encounter, boolean changed, long tick) {
        var world = replans.getOrDefault(encounter.worldId(), 0);
        var duePlan = changed || Math.floorMod(tick, caps.replanPeriodTicks()) == 0;
        if (!duePlan) {
            return false;
        }
        if (world >= caps.maxReplansPerTick() || (over && world > 0)) {
            metrics.add(DecisionMetrics.GroupCounter.GROUP_REPLAN_DEFERRED, 1);
            return false;
        }
        replans.put(encounter.worldId(), world + 1);
        return true;
    }

    /** Publishes the copies, cues and targets, or keeps the ledgers fair when the budget ran out (M25). */
    private void publish(Encounter encounter, GroupState state, long tick, boolean refresh) {
        if (over && !refresh) {
            var stale = state.degradeFair();
            if (stale > 0) {
                metrics.add(DecisionMetrics.GroupCounter.GROUP_COPY_STALE_TICKS, stale);
            }
            return;
        }
        state.publish(tick, caps);
        state.cue(tick, cues);
        state.assignTargets(encounter, caps, assignment, metrics, tick);
    }
}
