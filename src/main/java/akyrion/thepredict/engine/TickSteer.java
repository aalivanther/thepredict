package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CastPreference;
import akyrion.thepredict.domain.EnemyAction;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.MicroBehaviour;
import akyrion.thepredict.domain.MicroPolicy;
import akyrion.thepredict.domain.MicroSide;
import akyrion.thepredict.domain.MicroSidePriority;
import akyrion.thepredict.domain.Reaction;
import akyrion.thepredict.domain.ReactionBand;
import akyrion.thepredict.domain.ReactionPolicy;
import akyrion.thepredict.domain.TickSignals;

/**
 * Micro of one visit of the tick layer in {@code FULL} (M20.5, M24.5, M25.5). A micro stays published at least
 * {@code minHold} ticks unless a survival reaction needs to move the mob, and an orbit flips its side on its own
 * schedule. A view of another generation is never changed. Server thread only.
 */
final class TickSteer {
    private static final MicroSide[] SIDES = MicroSide.values();

    private TickSteer() {
    }

    /**
     * Chooses and publishes the micro of one session whose signals of this tick are valid.
     *
     * @param session the session
     * @param signals packed signals of this tick
     * @param tick current tick
     * @param board intent board
     * @param steps tells whether an evade choice is ready
     * @param minHold minimum ticks a micro stays published
     * @param tap tells a micro change when its detail is full
     */
    static void steer(MobSession session, long signals, long tick, IntentBoard board, ExecutionSteps steps,
                      int minHold, DecisionTap tap) {
        board.noteSteer(session.key().entityId(), session.seed(), session.profile().movement());
        if (tick < board.microNativeUntil(session.key().entityId())) {
            if (session.micro() != MicroBehaviour.NATIVE) {
                publish(board, session, MicroBehaviour.NATIVE, session.microSide(), tick, tap);
            }
            return;
        }
        var behaviour = groupMicro(session, desired(session, signals, tick, steps), tick);
        var survival = session.slot(ReactionBand.SURVIVAL) >= 0 && session.slotUntil(ReactionBand.SURVIVAL) > tick;
        if (behaviour != session.micro() && !survival && session.microSince() >= 0
                && tick - session.microSince() < minHold) {
            return;
        }
        if (behaviour == session.micro()) {
            if (behaviour == MicroBehaviour.ORBIT && patternSide(session, tick) != session.microSide().ordinal()
                    && MicroPolicy.flipOrbit(session.seed(), tick, session.microSince(), false)) {
                var flipped = session.microSide() == MicroSide.LEFT ? MicroSide.RIGHT : MicroSide.LEFT;
                publish(board, session, behaviour, flipped, tick, tap);
            }
            return;
        }
        var side = side(session, behaviour, signals, tick);
        if (behaviour == MicroBehaviour.SIDESTEP) {
            session.sidestepped(true);
        }
        publish(board, session, behaviour, side, tick, tap);
    }

    /**
     * Freezes the micro of a session the visit cap left out on the highest degradation step (M20.3).
     *
     * @param board intent board
     * @param session the session
     * @param tap tells a micro change when its detail is full
     */
    static void freeze(IntentBoard board, MobSession session, DecisionTap tap) {
        if (session.micro() == MicroBehaviour.NATIVE) {
            return;
        }
        publish(board, session, MicroBehaviour.NATIVE, session.microSide(), session.microSince(), tap);
    }

    /**
     * Side of a new orbit or sidestep (M26.5): terrain and the group gap rule it downstream (the destination check and
     * the slot), so here a habit of the target that strafes to one side, or a confident forecast of a strafe, wins over
     * the seed ({@link MicroSidePriority}). The side of a shield is geometry and stays.
     */
    private static MicroSide side(MobSession session, MicroBehaviour behaviour, long signals, long tick) {
        var reaction = session.reactionSlot(tick);
        var seeded = MicroPolicy.side(behaviour, reaction, signals, session.seed(), tick, session.microSide(),
                !session.sidestepped());
        if (reaction == Reaction.RESPECT_SHIELD.ordinal()) {
            return seeded;
        }
        return SIDES[MicroSidePriority.choose(-1, -1, patternSide(session, tick), seeded.ordinal())];
    }

    /**
     * The side that keeps the mob in front of a strafing target, or {@code -1} when the patterns have no opinion. A
     * strafe to the target's right is a move along {@code (-wz, wx)}, with {@code w} from the target to the mob, which
     * is where a left orbit takes the mob ({@code KinematicStamp.motion}, {@code Steering.orbit}): the mob mirrors the
     * strafe and the target never gets around it. Only an exploit the policy allows now uses it: {@code bias} or
     * {@code full}, the quota, the rest and entropy's {@code PASS} ({@link PatternCast#liveExploit}).
     *
     * @param session the session, with the pattern share of this visit already written
     * @param tick current tick
     * @return a {@link MicroSide} ordinal, or {@code -1}
     */
    static int patternSide(MobSession session, long tick) {
        if (!PatternCast.liveExploit(session, (int) tick)) {
            return -1;
        }
        var held = session.patternHeld();
        var left = (held & EnemyPattern.PREDICTABLE_STRAFE_LEFT.bit()) != 0;
        var right = (held & EnemyPattern.PREDICTABLE_STRAFE_RIGHT.bit()) != 0;
        if (left != right) {
            return (left ? MicroSide.RIGHT : MicroSide.LEFT).ordinal();
        }
        var forecast = session.patternForecast();
        if (forecast == EnemyAction.STRAFE_LEFT.ordinal()) {
            return MicroSide.RIGHT.ordinal();
        }
        return forecast == EnemyAction.STRAFE_RIGHT.ordinal() ? MicroSide.LEFT.ordinal() : -1;
    }

    private static MicroBehaviour desired(MobSession session, long signals, long tick, ExecutionSteps steps) {
        var chosen = MicroPolicy.choose(session.actionStance(), TickReactions.movement(session), signals,
                session.reactionSlot(tick), evadeReady(session, tick, steps), session.playerTarget(), 3.0,
                session.profile().movement());
        return MicroPolicy.awayFromEdge(chosen, session.zoneGuard(),
                TickSignals.push(signals, TickSignals.MOB_PUSH_NEAR));
    }

    private static MicroBehaviour groupMicro(MobSession session, MicroBehaviour behaviour, long tick) {
        var copy = session.groupCopy();
        if (copy.mode() == GroupMode.OFF) {
            return behaviour;
        }
        if (behaviour == MicroBehaviour.ENTER) {
            copy.askMelee();
            return copy.holdsMelee() ? behaviour : MicroBehaviour.HOVER;
        }
        if (behaviour == MicroBehaviour.NATIVE && copy.mode().rank() >= GroupMode.COORDINATE.rank()
                && !copy.holdsMelee() && copy.slotFar() && session.reactionSlot(tick) < 0) {
            return MicroBehaviour.SLOT;
        }
        return behaviour;
    }

    /** An escape, defense or sustain choice that fits the evade mask and can be cast now. */
    private static boolean evadeReady(MobSession session, long tick, ExecutionSteps steps) {
        var mask = ReactionPolicy.mask(Reaction.EVADE_CRITICAL);
        var choices = session.listedChoices();
        for (var index = 0; index < choices.length; index++) {
            if (session.choiceHeld(index, tick)) {
                continue;
            }
            var purpose = choices[index].purpose();
            if (purpose.isEmpty() || !CastPreference.fits(mask, purpose.get())) {
                continue;
            }
            if (steps.choiceReady(session, choices[index], tick)) {
                return true;
            }
        }
        return false;
    }

    private static void publish(IntentBoard board, MobSession session, MicroBehaviour behaviour, MicroSide side,
                                long tick, DecisionTap tap) {
        var from = session.micro();
        session.micro(behaviour, side);
        if (tap.detail() == TapDetail.FULL && from != behaviour) {
            tap.microChanged(session.key(), from, behaviour);
        }
        session.noteMicro(tick);
        var view = board.current(session.key().entityId());
        if (view.generation() != session.key().generation()) {
            return;
        }
        if (view.micro() != behaviour || view.microSide() != side) {
            board.publish(session.key(), view.withMicro(behaviour, side));
        }
    }
}
