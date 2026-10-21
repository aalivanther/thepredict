package akyrion.thepredict.domain;

/**
 * Pure stance transitions (M23.1). Writes into a caller-owned array and allocates nothing.
 *
 * <p>The array is {@code state, cause ordinal or -1, actionUntil, busyUntil, baseSince}. A cause of {@code -1} means
 * the word did not change. While the action is not {@code NONE}, {@code busyUntil} equals the deadline just written.
 * Closing to {@code NONE} sets {@code busyUntil} to {@code now}. During {@code RECOVERY} that deadline keeps every
 * damaging path blocked, so the mob does not attack; the only cast that may start before it is the one
 * {@link ReadyCastOrder#startsWhileBusy} admits (decision 6 of the user).
 */
public final class StanceMachine {
    /** Packed state. */
    public static final int OUT_STATE = 0;
    /** {@link StanceCause#ordinal()}, or {@code -1} when nothing changed. */
    public static final int OUT_CAUSE = 1;
    /** Tick the action ends. */
    public static final int OUT_ACTION_UNTIL = 2;
    /** Busy deadline rewritten by this step. */
    public static final int OUT_BUSY = 3;
    /** Tick the current base started. */
    public static final int OUT_BASE_SINCE = 4;
    /** No event this step. */
    public static final int EVENT_NONE = 0;
    /** A tell opened. {@code eventTicks} is the telegraph length and {@code whiffFloor} is the recovery. */
    public static final int EVENT_TELEGRAPH = 1;
    /** Impact. {@code eventTicks} is recovery. */
    public static final int EVENT_IMPACT = 2;
    /** Whiff. {@code eventTicks} is the choice recovery. */
    public static final int EVENT_WHIFF = 3;
    /** Target lost. */
    public static final int EVENT_LOST = 4;
    /** MythicMobs rejected the release. */
    public static final int EVENT_REJECTED = 5;
    /** Poise broke. {@code eventTicks} is stagger ticks. */
    public static final int EVENT_POISE = 6;
    /** Feint. Closes the wind-up with no recovery. The stance cause is {@link StanceCause#WHIFF}. */
    public static final int EVENT_FEINT = 7;
    /** Session invalidated. */
    public static final int EVENT_INVALIDATE = 8;
    /** Debug write of the desired base. */
    public static final int EVENT_DEBUG = 9;

    private StanceMachine() {
    }

    /**
     * One step.
     *
     * @param out length at least 5, owned by the caller
     * @param state packed state
     * @param now server tick
     * @param baseSince tick the current base started
     * @param actionUntil tick the action ends
     * @param desiredBase desired base ordinal
     * @param commitCause cause when that base commits and survival, dwell and deferral do not apply
     * @param survival whether evade is live
     * @param controlled whether ThePredict controls the mob
     * @param enraged whether enraged should be set
     * @param hysteresisCycles cycles required, 1–7
     * @param minDwellTicks dwell required
     * @param event one of the {@code EVENT_} constants
     * @param eventTicks telegraph, recovery or stagger ticks
     * @param whiffFloor recovery added after a whiff, a lost target or a rejection; recovery of a new tell
     */
    public static void step(long[] out, long state, long now, long baseSince, long actionUntil, int desiredBase,
                            int commitCause, boolean survival, boolean controlled, boolean enraged,
                            int hysteresisCycles, int minDwellTicks, int event, int eventTicks, int whiffFloor) {
        long next = state;
        long until = actionUntil;
        long since = baseSince;
        int cause = -1;
        boolean ended = false;
        int action = StanceState.action(next);
        if (event == EVENT_INVALIDATE) {
            write(out, StanceState.INITIAL, StanceCause.INVALIDATED.ordinal(), now, now, now);
            return;
        }
        if (event == EVENT_DEBUG) {
            write(out, StanceState.withEnraged(StanceState.applyBase(next, desiredBase), enraged),
                    StanceCause.DEBUG.ordinal(), until, until, now);
            return;
        }
        if (event == EVENT_POISE && action != ActionStance.STAGGERED.ordinal()) {
            next = StanceState.withAction(next, ActionStance.STAGGERED.ordinal());
            until = now + eventTicks;
            cause = StanceCause.POISE_BREAK.ordinal();
        } else if (event == EVENT_TELEGRAPH && action != ActionStance.STAGGERED.ordinal()) {
            next = StanceState.withAction(next, ActionStance.WINDUP.ordinal());
            until = now + eventTicks;
            cause = StanceCause.TELEGRAPH_START.ordinal();
            out[OUT_BUSY] = until + Math.max(0, whiffFloor);
        } else if (action == ActionStance.WINDUP.ordinal() && event >= EVENT_IMPACT && event <= EVENT_FEINT) {
            int ticks = windupTicks(event, eventTicks, whiffFloor);
            int closed = ticks <= 0 ? ActionStance.NONE.ordinal() : ActionStance.RECOVERY.ordinal();
            next = StanceState.withAction(next, closed);
            until = closed == ActionStance.NONE.ordinal() ? now : now + ticks;
            cause = windupCause(event);
            ended = closed == ActionStance.NONE.ordinal();
        } else if (action != ActionStance.NONE.ordinal() && event == EVENT_NONE && now >= until) {
            next = StanceState.withAction(next, ActionStance.NONE.ordinal());
            until = now;
            cause = action == ActionStance.STAGGERED.ordinal() ? StanceCause.STAGGER_END.ordinal()
                    : StanceCause.RECOVERY_END.ordinal();
            ended = true;
        }
        int want = !controlled ? BaseStance.NEUTRAL.ordinal() : survival ? BaseStance.EVASIVE.ordinal() : desiredBase;
        int baseCause = !controlled ? StanceCause.BASE_LEVEL.ordinal()
                : survival ? StanceCause.SURVIVAL.ordinal() : commitCause;
        if (StanceState.action(next) != ActionStance.NONE.ordinal()) {
            if (want == StanceState.base(next)) {
                next = StanceState.clearPending(next);
            } else {
                int cycles = StanceState.pending(next) && StanceState.pendingBase(next) == want
                        ? Math.min(7, StanceState.cycles(next) + 1) : 1;
                next = StanceState.pend(next, want, cycles);
            }
        } else if (want != StanceState.base(next)) {
            boolean immediate = survival || !controlled;
            boolean deferred = ended && StanceState.pending(state);
            int cycles = StanceState.pending(next) && StanceState.pendingBase(next) == want
                    ? Math.min(7, StanceState.cycles(next) + 1) : 1;
            if (immediate || deferred || (cycles >= hysteresisCycles && now - since >= minDwellTicks)) {
                next = StanceState.applyBase(next, want);
                since = now;
                if (cause < 0) {
                    cause = immediate ? baseCause : deferred ? StanceCause.DEFERRED_APPLIED.ordinal()
                            : StanceCause.DWELL_RELEASED.ordinal();
                }
            } else {
                next = StanceState.pend(next, want, cycles);
            }
        } else {
            next = StanceState.clearPending(next);
        }
        if (StanceState.enraged(state) != enraged) {
            next = StanceState.withEnraged(next, enraged);
            if (cause < 0) {
                cause = enraged ? StanceCause.ENRAGE_ON.ordinal() : StanceCause.ENRAGE_OFF.ordinal();
            }
        }
        long busy = cause == StanceCause.TELEGRAPH_START.ordinal() ? out[OUT_BUSY] : until;
        if (cause < 0) {
            busy = actionUntil;
        }
        write(out, next, cause, until, busy, since);
    }

    private static int windupTicks(int event, int recovery, int whiffFloor) {
        if (event == EVENT_FEINT) {
            return 0;
        }
        if (event == EVENT_IMPACT) {
            return Math.max(0, recovery);
        }
        return Math.max(Math.max(0, recovery), Math.max(0, whiffFloor));
    }

    private static int windupCause(int event) {
        return switch (event) {
            case EVENT_LOST -> StanceCause.TARGET_LOST.ordinal();
            case EVENT_REJECTED -> StanceCause.REJECTED.ordinal();
            case EVENT_WHIFF, EVENT_FEINT -> StanceCause.WHIFF.ordinal();
            default -> StanceCause.IMPACT.ordinal();
        };
    }

    private static void write(long[] out, long state, int cause, long until, long busy, long since) {
        out[OUT_STATE] = state;
        out[OUT_CAUSE] = cause;
        out[OUT_ACTION_UNTIL] = until;
        out[OUT_BUSY] = busy;
        out[OUT_BASE_SINCE] = since;
    }
}
