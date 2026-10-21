package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActionStance;
import akyrion.thepredict.domain.DangerRules;
import akyrion.thepredict.domain.ImpactTiming;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.ReleaseOutcome;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.StanceCause;
import akyrion.thepredict.domain.StanceMachine;
import akyrion.thepredict.domain.TelegraphEnd;
import akyrion.thepredict.domain.TelegraphPolicy;
import akyrion.thepredict.domain.TickSignals;

import java.util.UUID;

/**
 * Releases one due tell outside {@code castIfReady} (M23.4). Allocates nothing: the stance step uses a reused array
 * on the session only through a local that the caller may pass. This method uses one array the caller owns.
 */
final class TelegraphRelease {
    private TelegraphRelease() {
    }

    /**
     * Releases the tell of one session. Preconditions 5 and 6 of M19.4 do not apply. Precondition 1 does.
     *
     * @param session the caster
     * @param tick server tick
     * @param actions casts and signals
     * @param step length at least 5, owned by the caller
     * @return the outcome
     */
    static ReleaseOutcome release(MobSession session, long tick, CombatActions actions, long[] step) {
        return release(session, tick, actions, step, (ignored, choice, target, at) -> {
        });
    }

    /**
     * Releases the tell. The hazard book is written only when the cast returns {@link ReleaseOutcome#RELEASED},
     * against the tell target passed to {@code actions.release}.
     *
     * @param session the caster
     * @param tick server tick
     * @param actions casts and signals
     * @param step length at least 5, owned by the caller
     * @param hazards notes an accepted release; the four-argument release passes a no-op
     * @return the outcome
     */
    static ReleaseOutcome release(MobSession session, long tick, CombatActions actions, long[] step,
                                  NotedCast hazards) {
        if (invalid(session, tick)) {
            return ReleaseOutcome.SESSION_INVALID;
        }
        var choice = session.listedChoices()[session.tellChoice()];
        if (session.tellFeint()) {
            close(session, tick, choice, TelegraphEnd.FEINTED, StanceMachine.EVENT_FEINT, 0, actions, step);
            return ReleaseOutcome.WHIFFED;
        }
        var target = session.tellTarget();
        if (target == null) {
            close(session, tick, choice, TelegraphEnd.TARGET_LOST, StanceMachine.EVENT_LOST,
                    TelegraphPolicy.whiffRecovery(session.tellRecovery(), session.profile().telegraph()), actions, step);
            return ReleaseOutcome.TARGET_LOST;
        }
        if (stillInvulnerable(session, choice) && session.releaseWaits() < horizon(session)) {
            return defer(session, tick, choice, actions, step);
        }
        var movement = session.intent().map(intent -> intent.movement()).orElse(Movement.NONE);
        var flag = session.tellInterrupt();
        var outcome = actions.release(session.key(), choice, movement, target, flag);
        if (outcome == ReleaseOutcome.RELEASED) {
            session.holdInterrupt(flag);
        }
        return settle(session, tick, choice, target, outcome, actions, step, hazards);
    }

    /** A session or a tell this release cannot serve. A tell whose choice is no longer listed is closed. */
    private static boolean invalid(MobSession session, long tick) {
        if (session.state() != akyrion.thepredict.domain.SessionState.ACTIVE
                || session.readyGeneration() != session.key().generation() || !session.tellOpen()) {
            return true;
        }
        var index = session.tellChoice();
        if (index >= 0 && index < session.listedChoices().length) {
            return false;
        }
        GroupTellClose.apply(session, TelegraphEnd.SESSION_INVALID, tick);
        session.closeTell();
        return true;
    }

    /** Applies what the release returned: the hit, a wait, a miss or an invalid session. */
    private static ReleaseOutcome settle(MobSession session, long tick, SkillChoice choice, UUID target,
                                         ReleaseOutcome outcome, CombatActions actions, long[] step,
                                         NotedCast hazards) {
        return switch (outcome) {
            case RELEASED -> landed(session, tick, choice, target, true, hazards);
            case DEFERRED_INVULNERABLE -> session.releaseWaits() < horizon(session)
                    ? defer(session, tick, choice, actions, step)
                    : landed(session, tick, choice, target, false, hazards);
            case WHIFFED -> missed(session, tick, choice, TelegraphEnd.WHIFFED, StanceMachine.EVENT_WHIFF, actions, step);
            case REJECTED -> missed(session, tick, choice, TelegraphEnd.REJECTED, StanceMachine.EVENT_REJECTED, actions,
                    step);
            case TARGET_LOST -> missed(session, tick, choice, TelegraphEnd.TARGET_LOST, StanceMachine.EVENT_LOST, actions,
                    step);
            case SESSION_INVALID -> {
                GroupTellClose.apply(session, TelegraphEnd.SESSION_INVALID, tick);
                session.closeTell();
                yield ReleaseOutcome.SESSION_INVALID;
            }
            case RELEASE_DEFERRED -> ReleaseOutcome.RELEASE_DEFERRED;
        };
    }

    /**
     * Closes the tell. The book is written only when {@code released} is true, against {@code tellTarget}.
     */
    private static ReleaseOutcome landed(MobSession session, long tick, SkillChoice choice, UUID tellTarget,
                                         boolean released, NotedCast hazards) {
        session.noteCastStarted(tick);
        if (choice.kind() == akyrion.thepredict.domain.ChoiceKind.VANILLA_MELEE) {
            session.nextMeleeTick(tick + IntentControl.VANILLA_MELEE_INTERVAL_TICKS);
        }
        session.lastDanger(DangerRules.classify(choice).ordinal());
        if (session.tellBusy() > session.busyUntilTick()) {
            session.busyUntil(session.tellBusy());
        }
        session.nextStanceTick(session.actionUntilTick());
        if (released) {
            hazards.noteHazard(session, choice, tellTarget, tick);
            if (OwnImpacts.hits(choice)) {
                session.ownImpacts().note(tellTarget, tick + choice.busyTicks(), OwnImpacts.weight(choice), tick);
            }
        }
        session.tellResults().enqueue(session, tick, TelegraphEnd.RELEASED);
        GroupTellClose.apply(session, TelegraphEnd.RELEASED, tick);
        session.closeTell();
        return ReleaseOutcome.RELEASED;
    }

    /** An accepted release. The four-argument release supplies a no-op. */
    @FunctionalInterface
    interface NotedCast {
        /**
         * Notes the cast MythicMobs accepted.
         *
         * @param session the caster
         * @param choice the choice that was released
         * @param target the tell target passed to {@code actions.release}
         * @param tick the server tick
         */
        void noteHazard(MobSession session, SkillChoice choice, UUID target, long tick);
    }

    /**
     * Waits one tick and stays in wind-up. The cap is the hittable horizon {@code h}, not the shown length
     * (errata E23-5). Exhausting it does not whiff: the caller releases.
     */
    private static ReleaseOutcome defer(MobSession session, long tick, SkillChoice choice, CombatActions actions,
                                        long[] step) {
        session.releaseWaits(session.releaseWaits() + 1);
        session.releaseTick(tick + 1);
        if (session.actionUntilTick() <= tick) {
            session.actionUntilTick(tick + 1);
        }
        session.nextStanceTick(tick + 1);
        session.releaseDeferred(true);
        return ReleaseOutcome.DEFERRED_INVULNERABLE;
    }

    /** Hittable ticks of the packed signals, or 0 when the reading is not valid. */
    private static int horizon(MobSession session) {
        var signals = session.tickSignals();
        if (!TickSignals.bit(signals, TickSignals.VALID)) {
            return 0;
        }
        return TickSignals.hittableTicks(signals);
    }

    private static ReleaseOutcome missed(MobSession session, long tick, SkillChoice choice, TelegraphEnd end, int event,
                                         CombatActions actions, long[] step) {
        close(session, tick, choice, end, event,
                TelegraphPolicy.whiffRecovery(session.tellRecovery(), session.profile().telegraph()), actions, step);
        return switch (end) {
            case WHIFFED -> ReleaseOutcome.WHIFFED;
            case REJECTED -> ReleaseOutcome.REJECTED;
            case TARGET_LOST -> ReleaseOutcome.TARGET_LOST;
            default -> ReleaseOutcome.WHIFFED;
        };
    }

    private static void close(MobSession session, long tick, SkillChoice choice, TelegraphEnd end, int event,
                              int recovery, CombatActions actions, long[] step) {
        var index = session.tellChoice();
        StanceClock.apply(session, tick, session.baseStance().ordinal(), false, true, false, event, recovery, 0, actions,
                step);
        if (index >= 0) {
            session.noteWhiff(index, tick + session.profile().telegraph().whiffCooldownTicks());
        }
        session.tellResults().enqueue(session, tick, end);
        GroupTellClose.apply(session, end, tick);
        session.closeTell();
        if (end == TelegraphEnd.FEINTED) {
            actions.cue(session.key(), akyrion.thepredict.domain.CueRequest.stance(
                    akyrion.thepredict.domain.CombatStance.visible(session.baseStance().ordinal(),
                            session.actionStance().ordinal())));
        }
    }

    /** Answer of the open choice, for a test that reads the class. */
    static akyrion.thepredict.domain.TellAnswer answer(SkillChoice choice) {
        return DangerRules.answer(choice);
    }

    /**
     * Whether the hit of this release would land while the target is still invulnerable (M20.4, errata E23-5). The
     * lead of the tell has already run, so only the skill's own wind-up {@code w} is left before the impact: the
     * release waits while {@code h > w}. Comparing against the shown length would let almost every enforced tell
     * against a player land in invulnerability.
     */
    private static boolean stillInvulnerable(MobSession session, SkillChoice choice) {
        if (choice.nativeFacts().damageIgnoresInvulnerability()) {
            return false;
        }
        var purpose = choice.purpose();
        var damaging = purpose.isPresent() ? purpose.get().damaging() : choice.nativeFacts().hasDamagingMechanic();
        if (!damaging) {
            return false;
        }
        var signals = session.tickSignals();
        if (!TickSignals.bit(signals, TickSignals.VALID)) {
            return false;
        }
        return ImpactTiming.mustWait(TickSignals.hittableTicks(signals), choice.busyTicks());
    }

    /** Whether the action still blocks a second cast. */
    static boolean winding(MobSession session) {
        return session.actionStance() == ActionStance.WINDUP;
    }
}
