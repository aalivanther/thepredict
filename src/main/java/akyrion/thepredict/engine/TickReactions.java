package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActionStance;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.Reaction;
import akyrion.thepredict.domain.ReactionBand;
import akyrion.thepredict.domain.ReactionMask;
import akyrion.thepredict.domain.ReactionPolicy;
import akyrion.thepredict.domain.ReactionScope;
import akyrion.thepredict.domain.TickLayerMode;

/**
 * Reactions of one visit of the tick layer (M20.4, M23.6). The arming tick of each trigger is kept only for the
 * reactions the selection could return with the session as it is, so a trigger that can never arm does not keep the
 * session {@code HOT} (M20.3). The action stance filters inside the walk of {@link ReactionPolicy}, and a reaction
 * whose band slot holds a better one does not use up the tick: the next band still has its turn. At most one reaction
 * arms per visit. Allocation-free; server thread only.
 */
final class TickReactions {
    private static final Reaction[] REACTIONS = Reaction.values();
    private static final int BANDS = ReactionBand.values().length;

    private TickReactions() {
    }

    /**
     * Tracks the triggers of this tick and arms at most one reaction.
     *
     * @param session the session, whose signals of this tick are valid
     * @param signals packed signals of this tick
     * @param tick current tick
     * @param tap tells a fired reaction when its detail is full
     */
    static void arm(MobSession session, long signals, long tick, DecisionTap tap) {
        var since = session.reactionArmedSince();
        var exploit = track(session, signals, tick, since);
        var stance = session.actionStance();
        if (stance == ActionStance.WINDUP || stance == ActionStance.STAGGERED) {
            return;
        }
        var chosen = choose(session, signals, tick, since, exploit);
        if (chosen < 0) {
            return;
        }
        var reaction = REACTIONS[chosen];
        session.armReaction(chosen, tick + reaction.preferenceTicks(), tick + reaction.cooldownTicks());
        if (tap.detail() == TapDetail.FULL) {
            tap.reactionFired(session.key(), reaction);
        }
    }

    /**
     * Movement of the intent, {@link Movement#NONE} without one. No allocation.
     *
     * @param session the session
     * @return the movement
     */
    static Movement movement(MobSession session) {
        var intent = session.intent();
        return intent.isPresent() ? intent.get().movement() : Movement.NONE;
    }

    /**
     * Vision of the last capture. A probe without facts has not seen the target.
     *
     * @param session the session
     * @return whether the last capture saw the target
     */
    static boolean probeSeen(MobSession session) {
        var facts = session.lastMobFacts();
        if (facts.isEmpty()) {
            return false;
        }
        var target = facts.get().target();
        return target.isPresent() && target.get().visible();
    }

    /** Updates the arming tick of every trigger and returns whether the pattern counter may arm. */
    private static boolean track(MobSession session, long signals, long tick, long[] since) {
        var timing = session.profile().timing();
        var moves = timing.tickLayer() == TickLayerMode.FULL;
        var movement = movement(session);
        var seen = probeSeen(session);
        session.patternShare(PatternCast.share(session));
        var exploit = PatternCast.liveExploit(session, (int) tick);
        var mask = session.levers().reactionMask();
        var critical = session.profile().health().criticalRatio();
        var settings = session.profile().movement();
        for (var reaction : REACTIONS) {
            var holds = selectable(session, reaction, timing.reactions(), mask, moves)
                    && (reaction != Reaction.PUNISH_RECHARGE || session.playerTarget())
                    && ReactionPolicy.trigger(reaction, signals, movement, critical, settings, seen,
                    session.busyUntilTick(), tick, settings.meleeReachBlocks(), session.displaceReady(), exploit);
            var slot = reaction.ordinal();
            since[slot] = holds ? (since[slot] < 0 ? tick : since[slot]) : -1L;
        }
        return exploit;
    }

    /**
     * Whether the selection could return this reaction with the session as it is: the scope and the lever mask admit
     * it, a movement reaction needs a layer that moves the mob, and the edge step needs the zone guard. The cooldown
     * and the action stance are left out, so the delay keeps counting through them as before.
     */
    private static boolean selectable(MobSession session, Reaction reaction, ReactionScope scope, long mask,
                                      boolean moves) {
        if (reaction == Reaction.EDGE_DANGER && !session.zoneGuard()) {
            return false;
        }
        return scope.admits(reaction.band()) && ReactionMask.admits(mask, reaction)
                && (moves || reaction.evaluatedWithoutMicro());
    }

    /**
     * The first reaction that may take its slot. When the slot of the selected one holds a better reaction, the
     * triggers of that band are hidden for one more walk and put back before returning.
     */
    private static int choose(MobSession session, long signals, long tick, long[] since, boolean exploit) {
        var chosen = select(session, signals, tick, since, exploit);
        for (var round = 1; round < BANDS && chosen >= 0 && !takesSlot(session, REACTIONS[chosen], tick); round++) {
            hide(since, REACTIONS[chosen].band());
            chosen = select(session, signals, tick, since, exploit);
        }
        unhide(since);
        return chosen >= 0 && takesSlot(session, REACTIONS[chosen], tick) ? chosen : ReactionPolicy.NONE;
    }

    private static int select(MobSession session, long signals, long tick, long[] since, boolean exploit) {
        var timing = session.profile().timing();
        var levers = session.levers();
        var delay = Math.max(timing.reactionDelayTicks(), levers.reactionDelayTicks());
        return ReactionPolicy.select(session.actionStance(), signals, tick, delay, timing.reactions(),
                levers.reactionMask(), timing.tickLayer() == TickLayerMode.FULL, true, movement(session),
                session.profile().health().criticalRatio(), session.profile().movement(), probeSeen(session),
                session.busyUntilTick(), session.reactionCooldown(), since, session.zoneGuard(),
                session.displaceReady(), exploit);
    }

    private static boolean takesSlot(MobSession session, Reaction reaction, long tick) {
        return ReactionPolicy.replaces(session.slot(reaction.band()), session.slotUntil(reaction.band()), tick,
                reaction);
    }

    /** Hides the armed triggers of one band from the next walk: {@code t} becomes {@code -2 - t}, below zero. */
    private static void hide(long[] since, ReactionBand band) {
        for (var reaction : REACTIONS) {
            var slot = reaction.ordinal();
            if (reaction.band() == band && since[slot] >= 0) {
                since[slot] = -2L - since[slot];
            }
        }
    }

    /** Puts back every trigger {@link #hide} hid. A trigger that does not hold stays at {@code -1}. */
    private static void unhide(long[] since) {
        for (var slot = 0; slot < since.length; slot++) {
            if (since[slot] <= -2L) {
                since[slot] = -2L - since[slot];
            }
        }
    }
}
