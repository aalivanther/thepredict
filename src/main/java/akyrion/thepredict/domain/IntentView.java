package akyrion.thepredict.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Minimal immutable view of the current intent of one mob; the only thing conditions and
 * placeholders read. Safe to read from any thread (rule T3).
 *
 * <p>The intent owns its first six fields. The others are published by other steps (micro, rung, posture, zone, group
 * and patterns) and a republished intent keeps them with {@link #carrying(IntentView)}.
 *
 * @param generation generation of the session that published it, or {@code -1} for {@link #ABSENT}
 * @param level control level
 * @param movement requested movement
 * @param tactic active tactic, {@link TacticId#NONE} when there is none
 * @param confidence confidence in {@code [0, 1]}
 * @param expiresAtNanos monotonic deadline from {@code System.nanoTime()}, compared overflow-safely
 * @param micro micro-behaviour published with the intent
 * @param microSide side of an orbit or a sidestep
 * @param pressure rung 0–8, or {@code -1} when pressure is not published
 * @param stance visible stance; {@link CombatStance#NEUTRAL} when none was published (M23.8)
 * @param enraged whether the enraged mark is on
 * @param poisePercent meter fill 0–100, or {@code -1} when poise is not published
 * @param answer answer of the open tell; empty when there is no tell, including {@link #ABSENT} and {@link #base(int)}
 * @param impactAtTick tick the open tell hits, or {@code -1}
 * @param zoneFacts bits of published zone facts; {@code 0} when none are published
 * @param pushDanger {@code 0} none, {@code 1} harm, {@code 2} lethal, {@code 3} unknown; {@code 0} without a ready zone
 * @param role published group role; empty when the group assigned none (M25). There is no {@code NONE} role
 * @param plan published group plan; empty when the group has none
 * @param tokenMask bits of held tokens; bit 0 is melee and bit 1 is ranged
 * @param squadSize members of the squad, or {@code 0}
 * @param slotIndex published slot, or {@code -1} when the group assigned none
 * @param targetPatternMask bits of active enemy patterns of the target; {@code 0} outside {@code bias} and {@code full}
 * @param forecast action ordinal of the forecast, or {@code -1} when there is none
 */
public record IntentView(int generation, ControlLevel level, Movement movement,
                         TacticId tactic, float confidence, long expiresAtNanos, MicroBehaviour micro,
                         MicroSide microSide, int pressure, CombatStance stance, boolean enraged, byte poisePercent,
                         Optional<TellAnswer> answer, long impactAtTick, int zoneFacts, byte pushDanger,
                         Optional<GroupRole> role, Optional<GroupPlan> plan, int tokenMask, int squadSize,
                         int slotIndex, int targetPatternMask, byte forecast) {
    private static final List<Optional<TellAnswer>> ANSWERS = present(TellAnswer.values());
    private static final List<Optional<GroupRole>> ROLES = present(GroupRole.values());
    private static final List<Optional<GroupPlan>> PLANS = present(GroupPlan.values());

    /** Returned when a mob has no session; never controls and needs no allocation to read. */
    public static final IntentView ABSENT = new IntentView(-1, ControlLevel.BASE, Movement.NONE,
            TacticId.NONE, 0.0f, 0L);

    /**
     * Validates the view and normalizes a confidence of {@code -0.0f} to {@code 0.0f}, so every reader
     * sees one zero.
     *
     * @throws NullPointerException if a component is {@code null}
     * @throws IllegalArgumentException if {@code generation < -1}, the confidence is outside {@code [0, 1]} or a
     *         published field is outside its range
     */
    public IntentView {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(movement, "movement");
        Objects.requireNonNull(tactic, "tactic");
        Objects.requireNonNull(micro, "micro");
        Objects.requireNonNull(microSide, "microSide");
        confidence = checkedConfidence(generation, confidence);
        checkPosture(pressure, stance, poisePercent, answer, impactAtTick);
        checkZone(zoneFacts, pushDanger);
        checkGroup(role, plan, tokenMask, squadSize, slotIndex);
        checkPatterns(targetPatternMask, forecast);
    }

    /**
     * View from before patterns were published (rule W5). The mask is zero and there is no forecast.
     *
     * @param generation generation of the session
     * @param level control level
     * @param movement requested movement
     * @param tactic active tactic
     * @param confidence confidence in {@code [0, 1]}
     * @param expiresAtNanos monotonic deadline
     * @param micro micro-behaviour
     * @param microSide side of an orbit or a sidestep
     * @param pressure rung 0–8, or {@code -1}
     * @param stance visible stance
     * @param enraged whether the enraged mark is on
     * @param poisePercent meter fill, or {@code -1}
     * @param answer answer of the open tell
     * @param impactAtTick tick the open tell hits, or {@code -1}
     * @param zoneFacts bits of published zone facts
     * @param pushDanger {@code 0} none, {@code 1} harm, {@code 2} lethal, {@code 3} unknown
     * @param role published group role
     * @param plan published group plan
     * @param tokenMask bits of held tokens
     * @param squadSize members of the squad
     * @param slotIndex published slot, or {@code -1}
     */
    public IntentView(int generation, ControlLevel level, Movement movement, TacticId tactic, float confidence,
                      long expiresAtNanos, MicroBehaviour micro, MicroSide microSide, int pressure,
                      CombatStance stance, boolean enraged, byte poisePercent, Optional<TellAnswer> answer,
                      long impactAtTick, int zoneFacts, byte pushDanger, Optional<GroupRole> role,
                      Optional<GroupPlan> plan, int tokenMask, int squadSize, int slotIndex) {
        this(generation, level, movement, tactic, confidence, expiresAtNanos, micro, microSide, pressure, stance,
                enraged, poisePercent, answer, impactAtTick, zoneFacts, pushDanger, role, plan, tokenMask, squadSize,
                slotIndex, 0, (byte) -1);
    }

    /**
     * View from before a group published a role (rule W5). Role and plan stay empty and the token mask is zero.
     *
     * @param generation generation of the session
     * @param level control level
     * @param movement requested movement
     * @param tactic active tactic
     * @param confidence confidence in {@code [0, 1]}
     * @param expiresAtNanos monotonic deadline
     * @param micro micro-behaviour
     * @param microSide side of an orbit or a sidestep
     * @param pressure rung 0–8, or {@code -1}
     * @param stance visible stance
     * @param enraged whether the enraged mark is on
     * @param poisePercent meter fill, or {@code -1}
     * @param answer answer of the open tell
     * @param impactAtTick tick the open tell hits, or {@code -1}
     * @param zoneFacts bits of published zone facts
     * @param pushDanger {@code 0} none, {@code 1} harm, {@code 2} lethal, {@code 3} unknown
     */
    public IntentView(int generation, ControlLevel level, Movement movement, TacticId tactic, float confidence,
                      long expiresAtNanos, MicroBehaviour micro, MicroSide microSide, int pressure,
                      CombatStance stance, boolean enraged, byte poisePercent, Optional<TellAnswer> answer,
                      long impactAtTick, int zoneFacts, byte pushDanger) {
        this(generation, level, movement, tactic, confidence, expiresAtNanos, micro, microSide, pressure, stance,
                enraged, poisePercent, answer, impactAtTick, zoneFacts, pushDanger, Optional.empty(),
                Optional.empty(), 0, 0, -1);
    }

    /**
     * View from before zone facts were published (rule W5). Both new fields stay absent.
     *
     * @param generation generation of the session
     * @param level control level
     * @param movement requested movement
     * @param tactic active tactic
     * @param confidence confidence in {@code [0, 1]}
     * @param expiresAtNanos monotonic deadline
     * @param micro micro-behaviour
     * @param microSide side of an orbit or a sidestep
     * @param pressure rung 0–8, or {@code -1}
     * @param stance visible stance
     * @param enraged whether the enraged mark is on
     * @param poisePercent meter fill, or {@code -1}
     * @param answer open tell answer
     * @param impactAtTick tick the tell hits, or {@code -1}
     */
    public IntentView(int generation, ControlLevel level, Movement movement, TacticId tactic, float confidence,
                      long expiresAtNanos, MicroBehaviour micro, MicroSide microSide, int pressure,
                      CombatStance stance, boolean enraged, byte poisePercent, Optional<TellAnswer> answer,
                      long impactAtTick) {
        this(generation, level, movement, tactic, confidence, expiresAtNanos, micro, microSide, pressure, stance,
                enraged, poisePercent, answer, impactAtTick, 0, (byte) 0);
    }

    /**
     * View from before stances were published (rule W5). Stance is neutral, the answer is empty and poise is absent.
     *
     * @param generation generation of the session
     * @param level control level
     * @param movement requested movement
     * @param tactic active tactic
     * @param confidence confidence in {@code [0, 1]}
     * @param expiresAtNanos monotonic deadline
     * @param micro micro-behaviour
     * @param microSide side of an orbit or a sidestep
     * @param pressure rung 0–8, or {@code -1}
     */
    public IntentView(int generation, ControlLevel level, Movement movement, TacticId tactic, float confidence,
                      long expiresAtNanos, MicroBehaviour micro, MicroSide microSide, int pressure) {
        this(generation, level, movement, tactic, confidence, expiresAtNanos, micro, microSide, pressure,
                CombatStance.NEUTRAL, false, (byte) -1, Optional.empty(), -1L);
    }

    /**
     * View of M20: pressure is not published.
     *
     * @param generation generation of the session, or {@code -1} for {@link #ABSENT}
     * @param level control level
     * @param movement requested movement
     * @param tactic active tactic
     * @param confidence confidence in {@code [0, 1]}
     * @param expiresAtNanos monotonic deadline
     * @param micro micro-behaviour
     * @param microSide side of an orbit or a sidestep
     */
    public IntentView(int generation, ControlLevel level, Movement movement, TacticId tactic, float confidence,
                      long expiresAtNanos, MicroBehaviour micro, MicroSide microSide) {
        this(generation, level, movement, tactic, confidence, expiresAtNanos, micro, microSide, -1);
    }

    /**
     * View of M19: native micro and the left side. Production that publishes a micro uses the full record.
     *
     * @param generation generation of the session, or {@code -1} for {@link #ABSENT}
     * @param level control level
     * @param movement requested movement
     * @param tactic active tactic
     * @param confidence confidence in {@code [0, 1]}
     * @param expiresAtNanos monotonic deadline
     */
    public IntentView(int generation, ControlLevel level, Movement movement, TacticId tactic, float confidence,
                      long expiresAtNanos) {
        this(generation, level, movement, tactic, confidence, expiresAtNanos, MicroBehaviour.NATIVE, MicroSide.LEFT);
    }

    /**
     * Builds the level-4 view of a session: no control, no movement and the {@code none} tactic.
     *
     * @param generation generation of the session, {@code >= 0}
     * @return a view that never controls
     */
    public static IntentView base(int generation) {
        if (generation < 0) {
            throw new IllegalArgumentException("generation must be >= 0, was " + generation);
        }
        return new IntentView(generation, ControlLevel.BASE, Movement.NONE, TacticId.NONE, 0.0f, 0L);
    }

    /**
     * Tells whether ThePredict controls the mob at the given instant. Allocation-free.
     *
     * @param nowNanos current monotonic time from {@code System.nanoTime()}
     * @return {@code true} if the level is not {@code BASE} and the deadline has not passed
     */
    public boolean controls(long nowNanos) {
        return level != ControlLevel.BASE && nowNanos - expiresAtNanos < 0;
    }

    /**
     * Copy that keeps the intent of this view and every field another step published on {@code published}: micro,
     * rung, posture, zone, group and patterns. A republished intent uses it so a new tactic or deadline never erases
     * what the group, the zone or the patterns already put on the board. A view of another generation, such as
     * {@link #ABSENT} or the board of a previous session, carries nothing and this view is returned.
     *
     * @param published the view currently on the board
     * @return the copy, one allocation
     */
    public IntentView carrying(IntentView published) {
        Objects.requireNonNull(published, "published");
        if (published.generation != generation) {
            return this;
        }
        return new IntentView(generation, level, movement, tactic, confidence, expiresAtNanos, published.micro,
                published.microSide, published.pressure, published.stance, published.enraged, published.poisePercent,
                published.answer, published.impactAtTick, published.zoneFacts, published.pushDanger, published.role,
                published.plan, published.tokenMask, published.squadSize, published.slotIndex,
                published.targetPatternMask, published.forecast);
    }

    /**
     * Copy with another micro-behaviour. An extension republishes the intent and must keep the micro.
     *
     * @param behaviour micro-behaviour
     * @param side side of an orbit or a sidestep
     * @return the copy
     */
    public IntentView withMicro(MicroBehaviour behaviour, MicroSide side) {
        return new IntentView(generation, level, movement, tactic, confidence, expiresAtNanos, behaviour, side,
                pressure, stance, enraged, poisePercent, answer, impactAtTick, zoneFacts, pushDanger, role, plan,
                tokenMask, squadSize, slotIndex, targetPatternMask, forecast);
    }

    /**
     * Copy with a published rung. {@code -1} clears it.
     *
     * @param rung rung 0–8, or {@code -1}
     * @return the copy
     */
    public IntentView withPressure(int rung) {
        return new IntentView(generation, level, movement, tactic, confidence, expiresAtNanos, micro, microSide, rung,
                stance, enraged, poisePercent, answer, impactAtTick, zoneFacts, pushDanger, role, plan, tokenMask,
                squadSize, slotIndex, targetPatternMask, forecast);
    }

    /**
     * Copy with the stance fields placeholders read. An answer ordinal below zero, or past the last answer, clears the
     * answer. Allocates only the copy.
     *
     * @param movement published movement
     * @param stance visible stance
     * @param enraged whether the enraged mark is on
     * @param poisePercent meter fill, or {@code -1}
     * @param answerOrdinal {@link TellAnswer#ordinal()}, or below zero when there is no tell
     * @param impactAtTick tick the tell hits, or {@code -1}
     * @return the copy
     */
    public IntentView withPosture(Movement movement, CombatStance stance, boolean enraged, byte poisePercent,
                                  int answerOrdinal, long impactAtTick) {
        return new IntentView(generation, level, movement, tactic, confidence, expiresAtNanos, micro, microSide,
                pressure, stance, enraged, poisePercent, ordinal(ANSWERS, answerOrdinal), impactAtTick, zoneFacts,
                pushDanger, role, plan, tokenMask, squadSize, slotIndex, targetPatternMask, forecast);
    }

    /**
     * Copy with published zone facts. A stance republish keeps the zone already on the board.
     *
     * @param zoneFacts bits of published zone facts
     * @param pushDanger {@code 0} none, {@code 1} harm, {@code 2} lethal, {@code 3} unknown
     * @return the copy
     */
    public IntentView withZone(int zoneFacts, byte pushDanger) {
        return new IntentView(generation, level, movement, tactic, confidence, expiresAtNanos, micro, microSide,
                pressure, stance, enraged, poisePercent, answer, impactAtTick, zoneFacts, pushDanger, role, plan,
                tokenMask, squadSize, slotIndex, targetPatternMask, forecast);
    }

    /**
     * Copy with the group fields placeholders read. Empty role and plan write {@code none}.
     *
     * @param role published role, or empty
     * @param plan published plan, or empty
     * @param tokenMask bits of held tokens
     * @param squadSize members of the squad
     * @return the copy
     */
    public IntentView withGroup(Optional<GroupRole> role, Optional<GroupPlan> plan, int tokenMask, int squadSize) {
        return withGroup(role, plan, tokenMask, squadSize, slotIndex);
    }

    /**
     * Copy with the group fields, including the published slot. The form of M25, kept while callers use it (rule
     * W5); {@link #withGroup(int, int, int, int, int)} takes no {@code Optional} (rule C4).
     *
     * @param role published role, or empty
     * @param plan published plan, or empty
     * @param tokenMask bits of held tokens
     * @param squadSize members of the squad
     * @param slotIndex published slot, or {@code -1}
     * @return the copy
     */
    public IntentView withGroup(Optional<GroupRole> role, Optional<GroupPlan> plan, int tokenMask, int squadSize,
                                int slotIndex) {
        return new IntentView(generation, level, movement, tactic, confidence, expiresAtNanos, micro, microSide,
                pressure, stance, enraged, poisePercent, answer, impactAtTick, zoneFacts, pushDanger, role, plan,
                tokenMask, squadSize, slotIndex, targetPatternMask, forecast);
    }

    /**
     * Copy with the group fields by ordinal, the way the group copy of a session stores them (M25, I18). An ordinal
     * below zero, or past the last constant, publishes none. Allocates only the copy.
     *
     * @param roleOrdinal {@link GroupRole#ordinal()}, or {@code -1} when the group assigned none
     * @param planOrdinal {@link GroupPlan#ordinal()}, or {@code -1} when the group has none
     * @param tokenMask bits of held tokens; bit 0 is melee and bit 1 is ranged
     * @param squadSize members of the squad, or {@code 0}
     * @param slotIndex published slot, or {@code -1}
     * @return the copy
     */
    public IntentView withGroup(int roleOrdinal, int planOrdinal, int tokenMask, int squadSize, int slotIndex) {
        return withGroup(ordinal(ROLES, roleOrdinal), ordinal(PLANS, planOrdinal), tokenMask, squadSize, slotIndex);
    }

    /**
     * Copy with the published pattern mask and forecast. Published only when they change.
     *
     * @param targetPatternMask bits of active patterns
     * @param forecast action ordinal, or {@code -1}
     * @return the copy
     */
    public IntentView withPatterns(int targetPatternMask, byte forecast) {
        return new IntentView(generation, level, movement, tactic, confidence, expiresAtNanos, micro, microSide,
                pressure, stance, enraged, poisePercent, answer, impactAtTick, zoneFacts, pushDanger, role, plan,
                tokenMask, squadSize, slotIndex, targetPatternMask, forecast);
    }

    /**
     * Copy with the patterns of the target as the vocabulary may show them (M26.6, M26.11, I19). Only {@code bias} and
     * {@code full} publish: {@code off} and {@code observe} leave the mask at zero and the forecast at none, so a mode
     * that must not change decisions does not change the author's skills either. A forecast ordinal outside
     * {@link EnemyAction} publishes none.
     *
     * @param mode effective pattern mode of the session
     * @param activeMask bits of the active patterns of the target, {@code >= 0}
     * @param forecastOrdinal {@link EnemyAction#ordinal()} of the forecast, or {@code -1}
     * @return the copy
     */
    public IntentView withPatterns(PatternMode mode, int activeMask, int forecastOrdinal) {
        return withPatterns(shownMask(mode, activeMask), shownForecast(mode, forecastOrdinal));
    }

    /**
     * Whether this view already shows the group of a session as {@link #withGroup(int, int, int, int, int)} would
     * publish it, so a caller can skip a republish. Allocation-free.
     *
     * @param roleOrdinal {@link GroupRole#ordinal()}, or {@code -1}
     * @param planOrdinal {@link GroupPlan#ordinal()}, or {@code -1}
     * @param tokenMask bits of held tokens
     * @param squadSize members of the squad
     * @param slotIndex published slot, or {@code -1}
     * @return {@code true} when nothing would change
     */
    public boolean showsGroup(int roleOrdinal, int planOrdinal, int tokenMask, int squadSize, int slotIndex) {
        return role.equals(ordinal(ROLES, roleOrdinal)) && plan.equals(ordinal(PLANS, planOrdinal))
                && this.tokenMask == tokenMask && this.squadSize == squadSize && this.slotIndex == slotIndex;
    }

    /**
     * Whether this view already shows the patterns of the target as
     * {@link #withPatterns(PatternMode, int, int)} would publish them, so a caller can skip a republish.
     * Allocation-free.
     *
     * @param mode effective pattern mode of the session
     * @param activeMask bits of the active patterns of the target
     * @param forecastOrdinal {@link EnemyAction#ordinal()} of the forecast, or {@code -1}
     * @return {@code true} when nothing would change
     */
    public boolean showsPatterns(PatternMode mode, int activeMask, int forecastOrdinal) {
        return targetPatternMask == shownMask(mode, activeMask) && forecast == shownForecast(mode, forecastOrdinal);
    }

    private static boolean patternsShown(PatternMode mode) {
        return Objects.requireNonNull(mode, "mode") == PatternMode.BIAS || mode == PatternMode.FULL;
    }

    private static int shownMask(PatternMode mode, int activeMask) {
        return patternsShown(mode) ? activeMask : 0;
    }

    private static byte shownForecast(PatternMode mode, int forecastOrdinal) {
        var valid = forecastOrdinal >= 0 && forecastOrdinal < EnemyAction.COUNT;
        return (byte) (patternsShown(mode) && valid ? forecastOrdinal : -1);
    }

    private static float checkedConfidence(int generation, float confidence) {
        if (generation < -1) {
            throw new IllegalArgumentException("generation must be >= -1, was " + generation);
        }
        if (!(confidence >= 0.0f && confidence <= 1.0f)) {
            throw new IllegalArgumentException("confidence must be within [0, 1], was " + confidence);
        }
        return confidence == 0.0f ? 0.0f : confidence;
    }

    private static void checkPosture(int pressure, CombatStance stance, byte poisePercent, Optional<TellAnswer> answer,
                                     long impactAtTick) {
        if (pressure < -1 || pressure > 8) {
            throw new IllegalArgumentException("pressure must be within [-1, 8], was " + pressure);
        }
        Objects.requireNonNull(stance, "stance");
        Objects.requireNonNull(answer, "answer");
        if (poisePercent < -1 || poisePercent > 100) {
            throw new IllegalArgumentException("poisePercent must be within [-1, 100], was " + poisePercent);
        }
        if (impactAtTick < -1) {
            throw new IllegalArgumentException("impactAtTick must be >= -1, was " + impactAtTick);
        }
    }

    private static void checkZone(int zoneFacts, byte pushDanger) {
        if (zoneFacts < 0) {
            throw new IllegalArgumentException("zoneFacts must be >= 0, was " + zoneFacts);
        }
        if (pushDanger < 0 || pushDanger > 3) {
            throw new IllegalArgumentException("pushDanger must be within [0, 3], was " + pushDanger);
        }
    }

    private static void checkGroup(Optional<GroupRole> role, Optional<GroupPlan> plan, int tokenMask, int squadSize,
                                   int slotIndex) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(plan, "plan");
        if (tokenMask < 0) {
            throw new IllegalArgumentException("tokenMask must be >= 0, was " + tokenMask);
        }
        if (squadSize < 0) {
            throw new IllegalArgumentException("squadSize must be >= 0, was " + squadSize);
        }
        if (slotIndex < -1) {
            throw new IllegalArgumentException("slotIndex must be >= -1, was " + slotIndex);
        }
    }

    private static void checkPatterns(int targetPatternMask, byte forecast) {
        if (targetPatternMask < 0) {
            throw new IllegalArgumentException("targetPatternMask must be >= 0, was " + targetPatternMask);
        }
        if (forecast < -1 || forecast >= EnemyAction.COUNT) {
            throw new IllegalArgumentException("forecast must be within [-1, " + (EnemyAction.COUNT - 1) + "], was "
                    + forecast);
        }
    }

    private static <T> Optional<T> ordinal(List<Optional<T>> present, int ordinal) {
        return ordinal < 0 || ordinal >= present.size() ? Optional.empty() : present.get(ordinal);
    }

    @SafeVarargs
    private static <T> List<Optional<T>> present(T... values) {
        var present = new ArrayList<Optional<T>>(values.length);
        for (var value : values) {
            present.add(Optional.of(value));
        }
        return List.copyOf(present);
    }
}
