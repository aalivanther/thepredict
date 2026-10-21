package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CueRequest;
import akyrion.thepredict.domain.DangerRules;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.StanceMachine;
import akyrion.thepredict.domain.StanceState;
import akyrion.thepredict.domain.TelegraphPolicy;
import akyrion.thepredict.domain.TellAnswer;

/**
 * Opens a tell inside the cast path (M23.4). An entity with tells off returns immediately and changes nothing.
 */
final class TellOpening {
    private TellOpening() {
    }

    /**
     * Casts a choice whose tell opened in this cast with a lead of 0 and hands MythicMobs the tell interrupt (M23.4,
     * SA-02). A cast that goes out keeps the flag as the session's in-flight interrupt until its wind-up ends, so a
     * poise break after the tell closed still cuts the skill.
     *
     * @param session the caster, with its tell open
     * @param choice the choice of the tell
     * @param movement movement of the intent
     * @param actions the cast port
     * @return the cast outcome; {@code NOT_ELIGIBLE} when the choice needs a target and none was captured
     */
    static ExecutionOutcome castTold(MobSession session, SkillChoice choice, Movement movement,
                                     CombatActions actions) {
        var flag = session.tellInterrupt();
        var expected = session.lastTargetId();
        ExecutionOutcome outcome;
        if (expected.isPresent()) {
            outcome = actions.cast(session.key(), choice, movement, expected.get(), flag);
        } else if (choice.requiresTarget()) {
            return ExecutionOutcome.NOT_ELIGIBLE;
        } else {
            outcome = actions.cast(session.key(), choice, movement, flag);
        }
        if (outcome == ExecutionOutcome.CAST) {
            session.holdInterrupt(flag);
        }
        return outcome;
    }

    /**
     * Opens a tell when the rules say so. A positive lead, or a feint, holds the cast.
     *
     * @param session the caster
     * @param choice the choice about to cast
     * @param movement movement of the intent
     * @param tick server tick
     * @param actions signals
     * @param step length at least 5, owned by the caller
     * @return whether the caller must not cast now
     */
    static boolean hold(MobSession session, SkillChoice choice, Movement movement, long tick, CombatActions actions,
                        long[] step) {
        if (session.tellOpen()) {
            return true;
        }
        var settings = session.profile().telegraph();
        int windUp = choice.busyTicks();
        boolean player = session.playerTarget();
        boolean entropy = session.difficulty().entropy();
        boolean applies = TelegraphPolicy.minimumApplies(player, entropy, settings);
        var danger = DangerRules.classify(choice);
        if (!TelegraphPolicy.opens(player, applies, danger, windUp)) {
            return false;
        }
        var answer = DangerRules.answer(choice);
        int shown = TelegraphPolicy.telegraphTicks(windUp, danger, answer, applies, settings);
        int commitment = entropy ? settings.entropyCommitmentPercent() : settings.commitmentPercent();
        boolean feint = shown > 0 && TelegraphPolicy.feint(session.seed(), session.tellSequence() + 1L, commitment);
        return open(session, choice, tick, shown, windUp, feint, answer, actions, step);
    }

    /** Opens the tell of the lengths {@link #hold} decided and starts the wind-up stance. */
    private static boolean open(MobSession session, SkillChoice choice, long tick, int shown, int windUp,
                                boolean feint, TellAnswer answer, CombatActions actions, long[] step) {
        int lead = TelegraphPolicy.leadTicks(shown, windUp);
        int recovery = TelegraphPolicy.recoveryTicks(choice, session.profile().telegraph());
        boolean hold = lead > 0 || feint;
        var target = session.lastTargetId().orElse(null);
        var advance = hold ? Math.max(lead, feint && lead == 0 ? shown : lead) : 0;
        session.openTell(choiceIndex(session, choice), tick, shown, advance, recovery, feint, target);
        session.tellResults().capture(session, choice, tick, shown, lead, recovery, feint, target);
        if (feint && lead == 0) {
            session.releaseTick(tick + shown);
            session.nextStanceTick(tick + shown);
        }
        session.windupAnswer(answer);
        StanceClock.apply(session, tick, session.baseStance().ordinal(), false, true,
                StanceState.enraged(session.stanceState()), StanceMachine.EVENT_TELEGRAPH, shown, hold ? 0 : recovery,
                actions, step);
        session.noteCastStarted(tick);
        actions.cue(session.key(), CueRequest.tell(answer));
        return hold;
    }

    /**
     * Whether this cast must open a tell. Direct casts are only the step-2 case: the minimum applies and the class
     * is {@code NONE} or {@code LIGHT} with a wind-up of 0, or there is no player and no minimum (errata E23-7).
     *
     * @param session the caster
     * @param choice the choice about to cast
     * @return whether {@link #hold} would open
     */
    static boolean opens(MobSession session, SkillChoice choice) {
        var settings = session.profile().telegraph();
        var windUp = choice.busyTicks();
        var player = session.playerTarget();
        var applies = TelegraphPolicy.minimumApplies(player, session.difficulty().entropy(), settings);
        return TelegraphPolicy.opens(player, applies, DangerRules.classify(choice), windUp);
    }

    /**
     * Ticks from now until the hit of this choice lands if it is cast now: the shown length when the cast opens a tell
     * ({@link #hold} computes the same length), else the author's wind-up. The precision of impact and the group book
     * use it, so a tell with a lead is judged by its real impact tick (M20.4, A13).
     *
     * @param session the caster
     * @param choice the choice about to cast
     * @return ticks until the impact
     */
    static int impactTicks(MobSession session, SkillChoice choice) {
        int windUp = choice.busyTicks();
        if (session.tellOpen() || !opens(session, choice)) {
            return windUp;
        }
        var settings = session.profile().telegraph();
        boolean applies = TelegraphPolicy.minimumApplies(session.playerTarget(), session.difficulty().entropy(),
                settings);
        return TelegraphPolicy.telegraphTicks(windUp, DangerRules.classify(choice), DangerRules.answer(choice), applies,
                settings);
    }

    /**
     * Puts back the tell's busy deadline after a lead-zero cast rewrites it.
     *
     * @param session the caster
     */
    static void restore(MobSession session) {
        if (session.tellOpen() && !session.tellHeld() && session.tellBusy() > session.busyUntilTick()) {
            session.busyUntil(session.tellBusy());
        }
    }

    /**
     * After a lead-zero cast, keeps the tell's busy deadline and closes the tell bookkeeping. The stance stays.
     *
     * @param session the caster
     */
    static void afterCast(MobSession session, long tick) {
        restore(session);
        if (session.tellOpen() && !session.tellHeld()) {
            session.tellResults().enqueue(session, tick, akyrion.thepredict.domain.TelegraphEnd.RELEASED);
            GroupTellClose.apply(session, akyrion.thepredict.domain.TelegraphEnd.RELEASED, tick);
            session.closeTell();
        }
    }

    private static int choiceIndex(MobSession session, SkillChoice choice) {
        var choices = session.listedChoices();
        for (var index = 0; index < choices.length; index++) {
            if (choices[index] == choice || choices[index].id().equals(choice.id())) {
                return index;
            }
        }
        return 0;
    }
}
