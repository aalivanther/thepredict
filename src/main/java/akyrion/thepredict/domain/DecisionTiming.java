package akyrion.thepredict.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Timing and thresholds of decisions for one profile. Immutable.
 *
 * <p>Invariant: every tick value {@code > 0}; {@code heartbeatTicks >= minimumDecisionIntervalTicks};
 * {@code maximumIntentTicks >= minimumDecisionIntervalTicks}; confidence and margin within
 * {@code [0, 1)}; {@code contingenciesPerMob} within {@code [0, 2]}. The configuration resolves the global
 * {@code decision.questionDetail} and {@code decision.fitChecks} as the defaults of every profile when it loads, so the
 * engine and the encoder read only these values (rule C13); fit checks are only sent with {@link QuestionDetail#FULL}.
 *
 * @param heartbeatTicks ticks between decisions when nothing happens
 * @param minimumDecisionIntervalTicks minimum ticks a tactic is held before switching
 * @param maximumIntentTicks lifetime of an intent in ticks
 * @param minimumConfidence answers below this confidence are rejected
 * @param switchMargin probability a new tactic must gain over the current one to replace it
 * @param contingenciesPerMob contingencies kept per intent
 * @param questionDetail detail of the questions about mobs of this profile (M16.2)
 * @param fitChecks whether a {@code noul} fit check per option is asked with the {@code NOW} question (M16.5)
 * @param castMode which skills fire as soon as they are ready (M19.5); the global {@code decision.castMode} resolved
 *        as the default of the profile
 * @param readyHorizonTicks ticks ahead within which a skill counts as ready for the question only, {@code 0} to
 *        {@value #MAX_READY_HORIZON_TICKS} (M19.6); the local planner never reads it
 * @param untypedCast whether a choice without a purpose may fire outside its tactic (M19.2); the global
 *        {@code decision.untypedCast}, copied onto every profile
 * @param tickLayer how much of the per-tick layer this profile runs (M20.3)
 * @param reactions which reaction bands are evaluated (M20.4)
 * @param reactionsAhead bands that fire before the choice of the intent (M20.4)
 * @param reactionDelayTicks uninterrupted ticks a trigger must hold, 0 to 20 (M20.4)
 * @param cadence how often the encounter asks (M20.7)
 * @param reactiveIntervalTicks minimum ticks between reactive questions, 4 to 20 (M20.7)
 * @param intentHorizonTicks lifetime of a reactive intent, at most {@code maximumIntentTicks} (M20.7)
 */
public record DecisionTiming(int heartbeatTicks, int minimumDecisionIntervalTicks,
                             int maximumIntentTicks, double minimumConfidence,
                             double switchMargin, int contingenciesPerMob, QuestionDetail questionDetail,
                             boolean fitChecks, CastMode castMode, int readyHorizonTicks,
                             UntypedCastMode untypedCast, TickLayerMode tickLayer, ReactionScope reactions,
                             Set<ReactionBand> reactionsAhead, int reactionDelayTicks, DecisionCadence cadence,
                             int reactiveIntervalTicks, int intentHorizonTicks) {
    /** Largest number of contingencies per mob. */
    public static final int MAX_CONTINGENCIES_PER_MOB = 2;
    /** Largest readiness horizon in ticks. */
    public static final int MAX_READY_HORIZON_TICKS = 100;

    /**
     * Validates the timing.
     *
     * @throws IllegalArgumentException if an invariant does not hold
     */
    public DecisionTiming {
        DomainChecks.requireRange(heartbeatTicks, 1, Integer.MAX_VALUE, "heartbeatTicks");
        DomainChecks.requireRange(minimumDecisionIntervalTicks, 1, Integer.MAX_VALUE, "minimumDecisionIntervalTicks");
        DomainChecks.requireRange(maximumIntentTicks, 1, Integer.MAX_VALUE, "maximumIntentTicks");
        if (heartbeatTicks < minimumDecisionIntervalTicks) {
            throw new IllegalArgumentException("heartbeatTicks must be >= minimumDecisionIntervalTicks, was "
                    + heartbeatTicks + " < " + minimumDecisionIntervalTicks);
        }
        if (maximumIntentTicks < minimumDecisionIntervalTicks) {
            throw new IllegalArgumentException("maximumIntentTicks must be >= minimumDecisionIntervalTicks, was "
                    + maximumIntentTicks + " < " + minimumDecisionIntervalTicks);
        }
        DomainChecks.requireHalfOpenUnit(minimumConfidence, "minimumConfidence");
        DomainChecks.requireHalfOpenUnit(switchMargin, "switchMargin");
        DomainChecks.requireRange(contingenciesPerMob, 0, MAX_CONTINGENCIES_PER_MOB, "contingenciesPerMob");
        Objects.requireNonNull(questionDetail, "questionDetail");
        Objects.requireNonNull(castMode, "castMode");
        DomainChecks.requireRange(readyHorizonTicks, 0, MAX_READY_HORIZON_TICKS, "readyHorizonTicks");
        Objects.requireNonNull(untypedCast, "untypedCast");
        Objects.requireNonNull(tickLayer, "tickLayer");
        Objects.requireNonNull(reactions, "reactions");
        reactionsAhead = Set.copyOf(Objects.requireNonNull(reactionsAhead, "reactionsAhead"));
        DomainChecks.requireRange(reactionDelayTicks, 0, 20, "reactionDelayTicks");
        Objects.requireNonNull(cadence, "cadence");
        DomainChecks.requireRange(reactiveIntervalTicks, 4, 20, "reactiveIntervalTicks");
        if (intentHorizonTicks < 1 || intentHorizonTicks > maximumIntentTicks) {
            throw new IllegalArgumentException("intentHorizonTicks must be within [1, maximumIntentTicks], was "
                    + intentHorizonTicks);
        }
    }

    /**
     * Timing of M19 (errata E20-3): casts, every reaction scope, no band ahead, no delay, standard cadence and the
     * intent horizon equal to {@code maximumIntentTicks}. Production passes the full timing.
     *
     * @param heartbeatTicks ticks between decisions when nothing happens
     * @param minimumDecisionIntervalTicks minimum ticks a tactic is held before switching
     * @param maximumIntentTicks lifetime of an intent in ticks
     * @param minimumConfidence answers below this confidence are rejected
     * @param switchMargin probability a new tactic must gain over the current one to replace it
     * @param contingenciesPerMob contingencies kept per intent
     * @param questionDetail detail of the questions about mobs of this profile
     * @param fitChecks whether fit checks are asked with the {@code NOW} question
     * @param castMode which skills fire as soon as they are ready
     * @param readyHorizonTicks readiness horizon of the question
     * @param untypedCast whether a choice without a purpose may fire outside its tactic
     */
    public DecisionTiming(int heartbeatTicks, int minimumDecisionIntervalTicks, int maximumIntentTicks,
                          double minimumConfidence, double switchMargin, int contingenciesPerMob,
                          QuestionDetail questionDetail, boolean fitChecks, CastMode castMode, int readyHorizonTicks,
                          UntypedCastMode untypedCast) {
        this(heartbeatTicks, minimumDecisionIntervalTicks, maximumIntentTicks, minimumConfidence, switchMargin,
                contingenciesPerMob, questionDetail, fitChecks, castMode, readyHorizonTicks, untypedCast,
                TickLayerMode.CASTS, ReactionScope.ALL, Set.of(), 0, DecisionCadence.STANDARD, 6, maximumIntentTicks);
    }

    /**
     * Creates a timing with {@link UntypedCastMode#TACTIC_ONLY} (rule W5). Production passes the global mode.
     *
     * @param heartbeatTicks ticks between decisions when nothing happens
     * @param minimumDecisionIntervalTicks minimum ticks a tactic is held before switching
     * @param maximumIntentTicks lifetime of an intent in ticks
     * @param minimumConfidence answers below this confidence are rejected
     * @param switchMargin probability a new tactic must gain over the current one to replace it
     * @param contingenciesPerMob contingencies kept per intent
     * @param questionDetail detail of the questions about mobs of this profile
     * @param fitChecks whether fit checks are asked with the {@code NOW} question
     * @param castMode which skills fire as soon as they are ready
     * @param readyHorizonTicks readiness horizon of the question
     */
    public DecisionTiming(int heartbeatTicks, int minimumDecisionIntervalTicks, int maximumIntentTicks,
                          double minimumConfidence, double switchMargin, int contingenciesPerMob,
                          QuestionDetail questionDetail, boolean fitChecks, CastMode castMode, int readyHorizonTicks) {
        this(heartbeatTicks, minimumDecisionIntervalTicks, maximumIntentTicks, minimumConfidence, switchMargin,
                contingenciesPerMob, questionDetail, fitChecks, castMode, readyHorizonTicks, UntypedCastMode.DEFAULT);
    }

    /**
     * Creates a timing with {@link CastMode#INTENT_ONLY} and horizon 0, the timing of every profile before M19 (rule
     * W5), so the doubles of the existing tests keep their parity. Only tests may use it: production code, the scenario
     * facts and the duel measure pass the full timing with {@link CastMode#DEFAULT} ({@code ArchitectureRulesTest}).
     *
     * @param heartbeatTicks ticks between decisions when nothing happens
     * @param minimumDecisionIntervalTicks minimum ticks a tactic is held before switching
     * @param maximumIntentTicks lifetime of an intent in ticks
     * @param minimumConfidence answers below this confidence are rejected
     * @param switchMargin probability a new tactic must gain over the current one to replace it
     * @param contingenciesPerMob contingencies kept per intent
     * @param questionDetail detail of the questions about mobs of this profile
     * @param fitChecks whether fit checks are asked with the {@code NOW} question
     * @throws IllegalArgumentException if an invariant does not hold
     */
    public DecisionTiming(int heartbeatTicks, int minimumDecisionIntervalTicks, int maximumIntentTicks,
                          double minimumConfidence, double switchMargin, int contingenciesPerMob,
                          QuestionDetail questionDetail, boolean fitChecks) {
        this(heartbeatTicks, minimumDecisionIntervalTicks, maximumIntentTicks, minimumConfidence, switchMargin,
                contingenciesPerMob, questionDetail, fitChecks, CastMode.INTENT_ONLY, 0, UntypedCastMode.DEFAULT);
    }

    /**
     * Creates a timing with the default detail {@link QuestionDetail#DEFAULT} and without fit checks, the timing of
     * every profile before M16 (rule W5), with {@link CastMode#INTENT_ONLY} and horizon 0. Production code passes the
     * full timing of the profile; this constructor stays only while tests use it.
     *
     * @param heartbeatTicks ticks between decisions when nothing happens
     * @param minimumDecisionIntervalTicks minimum ticks a tactic is held before switching
     * @param maximumIntentTicks lifetime of an intent in ticks
     * @param minimumConfidence answers below this confidence are rejected
     * @param switchMargin probability a new tactic must gain over the current one to replace it
     * @param contingenciesPerMob contingencies kept per intent
     * @throws IllegalArgumentException if an invariant does not hold
     */
    public DecisionTiming(int heartbeatTicks, int minimumDecisionIntervalTicks, int maximumIntentTicks,
                          double minimumConfidence, double switchMargin, int contingenciesPerMob) {
        this(heartbeatTicks, minimumDecisionIntervalTicks, maximumIntentTicks, minimumConfidence, switchMargin,
                contingenciesPerMob, QuestionDetail.DEFAULT, false, CastMode.INTENT_ONLY, 0, UntypedCastMode.DEFAULT);
    }

    /**
     * Returns a copy with another cast mode and readiness horizon, keeping everything else.
     *
     * @param mode the cast mode
     * @param horizonTicks the readiness horizon
     * @return the copy
     */
    public DecisionTiming withCasting(CastMode mode, int horizonTicks) {
        return new DecisionTiming(heartbeatTicks, minimumDecisionIntervalTicks, maximumIntentTicks, minimumConfidence,
                switchMargin, contingenciesPerMob, questionDetail, fitChecks, mode, horizonTicks, untypedCast,
                tickLayer, reactions, reactionsAhead, reactionDelayTicks, cadence, reactiveIntervalTicks,
                intentHorizonTicks);
    }

    /**
     * Returns a copy with the per-tick layer fields.
     *
     * @param mode tick layer mode
     * @param scope reaction scope
     * @param ahead bands ahead of the intent choice
     * @param delayTicks reaction delay
     * @param nextCadence cadence
     * @param intervalTicks reactive interval
     * @param horizonTicks intent horizon
     * @return the copy
     */
    public DecisionTiming withLayer(TickLayerMode mode, ReactionScope scope, Set<ReactionBand> ahead, int delayTicks,
                                    DecisionCadence nextCadence, int intervalTicks, int horizonTicks) {
        return new DecisionTiming(heartbeatTicks, minimumDecisionIntervalTicks, maximumIntentTicks, minimumConfidence,
                switchMargin, contingenciesPerMob, questionDetail, fitChecks, castMode, readyHorizonTicks, untypedCast,
                mode, scope, ahead, delayTicks, nextCadence, intervalTicks, horizonTicks);
    }

    /**
     * Tells whether fit checks are really sent: only when they are on and the detail is {@link QuestionDetail#FULL}.
     *
     * @return {@code fitChecks && questionDetail == FULL}
     */
    public boolean sendsFitChecks() {
        return fitChecks && questionDetail == QuestionDetail.FULL;
    }
}
