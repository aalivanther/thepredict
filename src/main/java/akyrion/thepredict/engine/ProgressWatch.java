package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.IntentPolicy;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.TacticalIntent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code NO_PROGRESS} rule of M05 and M10.4, read once per evaluation of an encounter through
 * {@link ExecutionSteps#evaluated}. Package-private; server thread only; it reads {@link MobControl#progress}, which
 * computes no path.
 *
 * <p>Only a controlling intent whose movement needs to move is judged: {@code APPROACH} until melee reach,
 * {@code RETREAT} until the retreat distance, {@code KEEP_DISTANCE} outside its band and {@code FLANK}. An evaluation
 * makes progress when a ThePredict movement goal runs and, for the first three, the target distance changed by at
 * least {@value #STEP_BLOCKS} blocks in the expected direction since the previous evaluation of the same tactic; with
 * no known distance only the goal counts. {@value #EVALUATIONS_FOR_NO_PROGRESS} evaluations in a row without progress
 * give {@code NO_PROGRESS}: the next alternative of the answer, or level 4 without one. A session held by the debug
 * command is not judged, and an exception drops only its session to level 4.
 *
 * <p>The rule measures time, not evaluations (PZ-07): an evaluation counts only once
 * {@code minimumDecisionIntervalTicks} have passed since the previous counted one, so a {@code REACTIVE} encounter
 * evaluated every few ticks is judged as often as a {@code STANDARD} one and the reference distance spans the same
 * time. The cast that follows an alternative goes through the plain start guard of {@code IntentControl.cast}, which
 * refuses a second start in the tick and a start during a wind-up. A local tactic dropped to level 4 and planned
 * again at once keeps the signal it already sent ({@code MobSession.resumesStuck}).
 */
final class ProgressWatch {
    static final double STEP_BLOCKS = 0.5;
    static final int EVALUATIONS_FOR_NO_PROGRESS = 2;

    private final MobControl mobs;
    private final IntentControl control;

    ProgressWatch(MobControl mobs, IntentControl control) {
        this.mobs = Objects.requireNonNull(mobs, "mobs");
        this.control = Objects.requireNonNull(control, "control");
    }

    /** Judges every active member of an evaluated encounter. */
    void evaluated(Encounter encounter, long tick, long nowNanos) {
        for (var member : List.copyOf(encounter.memberSessions())) {
            if (member.state() != SessionState.ACTIVE || control.heldByDebug(member, nowNanos)) {
                continue;
            }
            try {
                judge(member, tick, nowNanos);
            } catch (RuntimeException exception) {
                control.fail(member, tick);
            }
        }
    }

    private void judge(MobSession session, long tick, long nowNanos) {
        var intent = session.intent().filter(found -> !found.expired(nowNanos) && found.level() != ControlLevel.BASE);
        if (intent.isEmpty() || !needsToMove(intent.get().movement())
                || !session.progressDue(tick, session.profile().timing().minimumDecisionIntervalTicks())) {
            return;
        }
        session.progressJudged(tick);
        var progress = mobs.progress(session.key());
        var moved = madeProgress(intent.get().movement(), session.profile().movement(), progress, session);
        progress.targetDistanceBlocks().ifPresentOrElse(session::progressReferenceBlocks, session::clearProgressReference);
        session.noProgressStreak(moved ? 0 : session.noProgressStreak() + 1);
        if (session.noProgressStreak() >= EVALUATIONS_FOR_NO_PROGRESS) {
            stuck(session, intent.get(), tick, nowNanos);
        }
    }

    private void stuck(MobSession session, TacticalIntent intent, long tick, long nowNanos) {
        session.resetProgress();
        var answered = intent.level() == ControlLevel.JEV || intent.level() == ControlLevel.ALTERNATIVE;
        var alternative = answered ? IntentPolicy.nextAlternative(intent, IntentControl.eligible(session),
                session.profile(), tick) : Optional.<TacticalIntent>empty();
        if (alternative.isPresent()) {
            control.apply(session, alternative.get(), tick);
            control.dropped(session, FallbackReason.NO_PROGRESS, tick);
            control.cast(session, tick, nowNanos);
        } else {
            session.noteStuck(intent.tactic(), tick);
            control.toBase(session, FallbackReason.NO_PROGRESS, tick);
        }
    }

    static boolean needsToMove(Movement movement) {
        return movement != Movement.HOLD && movement != Movement.NONE;
    }

    /**
     * Tells whether one evaluation made progress against the reference distance kept in the session.
     *
     * @param movement movement of the intent
     * @param settings movement settings of the profile
     * @param progress reading of this evaluation
     * @param session session whose previous reading of the same tactic is the reference
     * @return {@code true} when the movement reached its aim or advanced in the expected direction
     */
    static boolean madeProgress(Movement movement, MovementSettings settings, MovementProgress progress,
                                MobSession session) {
        if (!needsToMove(movement)) {
            return true;
        }
        var distance = progress.targetDistanceBlocks();
        if (movement != Movement.FLANK && distance.isPresent() && reached(movement, settings, distance.getAsDouble())) {
            return true;
        }
        if (!progress.movementGoalRunning()) {
            return false;
        }
        var previous = session.progressReferenceBlocks();
        return movement == Movement.FLANK || distance.isEmpty() || previous.isEmpty()
                || advanced(movement, settings, distance.getAsDouble(), previous.getAsDouble());
    }

    /**
     * Tells whether the distance moved at least {@value #STEP_BLOCKS} blocks in the direction the movement expects.
     *
     * @param movement {@code APPROACH}, {@code RETREAT} or {@code KEEP_DISTANCE}
     * @param settings movement settings of the profile
     * @param distanceBlocks target distance now
     * @param previousBlocks target distance at the previous evaluation
     * @return {@code true} when it closed in, backed away or returned toward the band as expected
     */
    static boolean advanced(Movement movement, MovementSettings settings, double distanceBlocks, double previousBlocks) {
        var change = distanceBlocks - previousBlocks;
        return switch (movement) {
            case APPROACH -> -change >= STEP_BLOCKS;
            case RETREAT -> change >= STEP_BLOCKS;
            default -> distanceBlocks < settings.bandInnerBlocks() ? change >= STEP_BLOCKS : -change >= STEP_BLOCKS;
        };
    }

    /** Tells whether the movement already reached its aim: melee reach, retreat distance or the band. */
    static boolean reached(Movement movement, MovementSettings settings, double distance) {
        return switch (movement) {
            case APPROACH -> distance <= settings.meleeReachBlocks() + STEP_BLOCKS;
            case RETREAT -> distance >= settings.retreatDistanceBlocks() - STEP_BLOCKS;
            case KEEP_DISTANCE -> distance >= settings.bandInnerBlocks() && distance <= settings.bandOuterBlocks();
            default -> false;
        };
    }
}
