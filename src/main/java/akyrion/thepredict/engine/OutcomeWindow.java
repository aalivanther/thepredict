package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.TacticId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What happened after one intent was adopted (M16.8, principle A9): at most one open window per session, from the
 * adoption of a new intent of level {@code JEV}, {@code ALTERNATIVE} or {@code LOCAL} (never a renewal) until the
 * intent ends or {@value #OUTCOME_WINDOW_TICKS} ticks pass, whichever comes first. Server thread only; owned by its
 * session and changed only by {@link OutcomeEvents}.
 *
 * <p>It sums the damage dealt and taken as fractions of the victim's maximum health, counts the casts of the epoch and
 * the confirmed hits (damage dealt to its target within {@value #HIT_WINDOW_TICKS} ticks after a damaging cast), keeps
 * the first and last distance band of the probes, and notes the death of the mob and of its target. Whether the
 * movement reached its goal: {@code APPROACH} saw {@code MELEE}; {@code KEEP_DISTANCE} ends in {@code RANGED};
 * {@code RETREAT} ends farther than it started, or {@code BEYOND}; {@code FLANK} cast while flanking; {@code HOLD} and
 * {@code NONE} have no goal and never count as reached.
 */
public final class OutcomeWindow {
    /** Longest window, in ticks. */
    public static final int OUTCOME_WINDOW_TICKS = 60;
    /** Ticks after a damaging cast within which damage dealt to the target confirms a hit. */
    public static final int HIT_WINDOW_TICKS = 20;
    /** Weight of a reached movement goal in {@link Summary#effect()}. */
    public static final double REACHED_WEIGHT = 0.25;

    private final ControlLevel level;
    private final TacticId tactic;
    private final Movement movement;
    private final long startTick;
    private final Optional<UUID> targetId;
    private final boolean contingency;
    private final double confidence;
    private Optional<DistanceBand> startBand = Optional.empty();
    private Optional<DistanceBand> endBand = Optional.empty();
    private boolean sawMelee;
    private double dealt;
    private double taken;
    private int casts;
    private int readyCasts;
    private int hits;
    private long lastDamagingCastTick = Long.MIN_VALUE / 2;
    private boolean mobDied;
    private boolean targetDied;

    OutcomeWindow(ControlLevel level, TacticId tactic, Movement movement, long startTick, Optional<UUID> targetId,
                  boolean contingency) {
        this(level, tactic, movement, startTick, targetId, contingency, 0.0);
    }

    /**
     * Opens a window that also keeps the confidence of the adopted intent (M22.6).
     *
     * @param level control level
     * @param tactic tactic
     * @param movement movement
     * @param startTick adoption tick
     * @param targetId target, or empty
     * @param contingency whether a contingency opened it
     * @param confidence confidence of the intent, {@code 0} to {@code 1}
     */
    OutcomeWindow(ControlLevel level, TacticId tactic, Movement movement, long startTick, Optional<UUID> targetId,
                  boolean contingency, double confidence) {
        this.level = Objects.requireNonNull(level, "level");
        this.tactic = Objects.requireNonNull(tactic, "tactic");
        this.movement = Objects.requireNonNull(movement, "movement");
        this.startTick = startTick;
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.contingency = contingency;
        this.confidence = confidence;
    }

    /**
     * What one closed window measured; the only summary type, used by {@link OutcomeMetrics}, {@code inspect} and the
     * duel recorder of M18. Immutable.
     *
     * @param level control level of the intent
     * @param tactic tactic of the intent
     * @param movement movement of the tactic
     * @param startTick tick of the adoption
     * @param endTick tick of the close
     * @param dealt damage dealt, in fractions of the victims' maximum health, {@code >= 0}
     * @param taken damage taken, in fractions of the mob's maximum health, {@code >= 0}
     * @param reached whether the movement reached its goal
     * @param casts casts of the epoch, {@code >= 0}
     * @param hits confirmed hits, {@code >= 0}
     * @param mobDied whether the mob died
     * @param targetDied whether its target died
     * @param effect comparative index {@code dealt − taken + 0.25·reached + targetDied − mobDied}; not "the truth", only a
     *        way to compare arms with the same profile
     * @param readyCasts casts of the epoch that were not the choice of the intent, {@code 0} to {@code casts} (M19.5)
     * @param startBand first distance band of the window, empty when no probe reported one (M21)
     * @param targetKind kind of the target, empty when the window does not know it (M21)
     */
    public record Summary(ControlLevel level, TacticId tactic, Movement movement, long startTick, long endTick,
                          double dealt, double taken, boolean reached, int casts, int hits, boolean mobDied,
                          boolean targetDied, double effect, int readyCasts, Optional<DistanceBand> startBand,
                          Optional<ParticipantKind> targetKind) {
        /**
         * Validates the summary.
         *
         * @throws NullPointerException if a component is {@code null}
         * @throws IllegalArgumentException if a sum or count is negative, or the end precedes the start
         */
        public Summary {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(tactic, "tactic");
            Objects.requireNonNull(movement, "movement");
            if (!(dealt >= 0.0) || !(taken >= 0.0) || casts < 0 || hits < 0 || readyCasts < 0
                    || readyCasts > casts || endTick < startTick) {
                throw new IllegalArgumentException("invalid outcome summary of tactic " + tactic.value());
            }
            startBand = Objects.requireNonNull(startBand, "startBand");
            targetKind = Objects.requireNonNull(targetKind, "targetKind");
        }

        /**
         * Creates a summary without a start band or a target kind (rule W5).
         *
         * @param level level of the intent
         * @param tactic tactic of the intent
         * @param movement movement of the intent
         * @param startTick tick the window opened
         * @param endTick tick the window closed
         * @param dealt damage dealt
         * @param taken damage taken
         * @param reached whether the movement reached its goal
         * @param casts casts of the epoch
         * @param hits confirmed hits
         * @param mobDied whether the mob died
         * @param targetDied whether its target died
         * @param effect comparative index
         * @param readyCasts ready casts of the epoch
         */
        public Summary(ControlLevel level, TacticId tactic, Movement movement, long startTick, long endTick,
                       double dealt, double taken, boolean reached, int casts, int hits, boolean mobDied,
                       boolean targetDied, double effect, int readyCasts) {
            this(level, tactic, movement, startTick, endTick, dealt, taken, reached, casts, hits, mobDied, targetDied,
                    effect, readyCasts, Optional.empty(), Optional.empty());
        }

        /**
         * Creates a summary with no ready-cast mark (rule W5): the form of every window before M19.
         *
         * @param level level of the intent
         * @param tactic tactic of the intent
         * @param movement movement of the intent
         * @param startTick tick the window opened
         * @param endTick tick the window closed
         * @param dealt damage dealt, in fractions of maximum health
         * @param taken damage taken, in fractions of maximum health
         * @param reached whether the movement reached its goal
         * @param casts casts of the epoch
         * @param hits confirmed hits
         * @param mobDied whether the mob died
         * @param targetDied whether its target died
         * @param effect comparative index
         */
        public Summary(ControlLevel level, TacticId tactic, Movement movement, long startTick, long endTick,
                       double dealt, double taken, boolean reached, int casts, int hits, boolean mobDied,
                       boolean targetDied, double effect) {
            this(level, tactic, movement, startTick, endTick, dealt, taken, reached, casts, hits, mobDied, targetDied,
                    effect, 0);
        }

        /**
         * The comparative index of a window.
         *
         * @param dealt damage dealt
         * @param taken damage taken
         * @param reached whether the movement reached its goal
         * @param targetDied whether the target died
         * @param mobDied whether the mob died
         * @return {@code dealt − taken + 0.25·reached + targetDied − mobDied}
         */
        public static double effect(double dealt, double taken, boolean reached, boolean targetDied, boolean mobDied) {
            return dealt - taken + (reached ? REACHED_WEIGHT : 0.0) + (targetDied ? 1.0 : 0.0) - (mobDied ? 1.0 : 0.0);
        }
    }

    /** The target the window judges its hits and the target's death by, empty without one. */
    Optional<UUID> targetId() {
        return targetId;
    }

    /** Whether a contingency opened this window (M16.6). */
    boolean contingency() {
        return contingency;
    }

    /** Confidence of the intent that opened this window (M22.6). */
    double confidence() {
        return confidence;
    }

    /** Whether the window reached its longest duration. */
    boolean due(long tick) {
        return tick - startTick >= OUTCOME_WINDOW_TICKS;
    }

    void probed(Optional<DistanceBand> band) {
        if (band.isEmpty()) {
            return;
        }
        if (startBand.isEmpty()) {
            startBand = band;
        }
        endBand = band;
        sawMelee |= band.get() == DistanceBand.MELEE;
    }

    void cast(boolean damaging, long tick) {
        cast(damaging, tick, false);
    }

    /**
     * Counts a cast. {@code ready} marks a candidate that was not the choice of the intent (M19.5).
     *
     * @param damaging whether the choice deals damage
     * @param tick current tick
     * @param ready whether the cast came from {@code ANY_READY} rather than the intent's choice
     */
    void cast(boolean damaging, long tick, boolean ready) {
        casts++;
        if (ready) {
            readyCasts++;
        }
        if (damaging) {
            lastDamagingCastTick = tick;
        }
    }

    void dealt(UUID victimId, double healthFraction, long tick) {
        dealt += unit(healthFraction);
        if (targetId.filter(victimId::equals).isPresent() && tick - lastDamagingCastTick <= HIT_WINDOW_TICKS) {
            hits++;
            lastDamagingCastTick = Long.MIN_VALUE / 2;
        }
    }

    void taken(double healthFraction) {
        taken += unit(healthFraction);
    }

    /** One hit is at most the whole health of its victim; a negative or undefined fraction adds nothing. */
    private static double unit(double healthFraction) {
        return healthFraction > 0.0 ? Math.min(1.0, healthFraction) : 0.0;
    }

    void mobDied() {
        mobDied = true;
    }

    void targetDied() {
        targetDied = true;
    }

    Summary close(long endTick) {
        var reached = reached();
        return new Summary(level, tactic, movement, startTick, Math.max(startTick, endTick), dealt, taken, reached,
                casts, hits, mobDied, targetDied, Summary.effect(dealt, taken, reached, targetDied, mobDied),
                readyCasts, startBand, Optional.empty());
    }

    private boolean reached() {
        return switch (movement) {
            case APPROACH -> sawMelee;
            case KEEP_DISTANCE -> endBand.filter(band -> band == DistanceBand.RANGED).isPresent();
            case RETREAT -> endBand.filter(band -> band == DistanceBand.BEYOND
                    || startBand.filter(start -> band.compareTo(start) > 0).isPresent()).isPresent();
            case FLANK -> casts > 0;
            case HOLD, NONE -> false;
        };
    }
}
