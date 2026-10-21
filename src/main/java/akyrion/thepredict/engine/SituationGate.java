package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.DecisionCadence;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PruningMode;
import akyrion.thepredict.domain.ReadyIn;
import akyrion.thepredict.domain.SituationRules;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TickSignals;

/**
 * Rule 8 of an answer whose situation changed (M20.7). Inactive while the cadence is standard and pruning is off,
 * which is the parity of M19. A signature that did not change never asks {@link SituationRules}.
 */
final class SituationGate {
    private SituationGate() {
    }

    /** What rule 8 does with one mob. */
    enum Mark {
        /** Standard cadence and pruning off: the answer is not aged and not vetoed. */
        INACTIVE,
        /** The live signature still matches the capture, or no capture was stored. */
        UNCHANGED,
        /** The signature changed and no live veto holds. */
        AGED,
        /** The signature changed and a live veto discards the mob. */
        VETO
    }

    /**
     * Rule 8 for an answer whose best tactic is not in the profile (AE-13): only the signature and the health decide.
     *
     * @param session the session
     * @param pruning {@code decision.pruning}
     * @param control reads the readiness of a choice
     * @return what rule 8 does with the mob
     */
    static Mark classify(MobSession session, PruningMode pruning, IntentControl control) {
        return judge(session, null, pruning, control);
    }

    /**
     * Rule 8 for the best tactic of an answer. The caller resolves the tactic first, so neither an {@code Optional}
     * nor a {@code null} travels as a parameter (C4, AE-13).
     *
     * @param session the session
     * @param best the best tactic of the answer
     * @param pruning {@code decision.pruning}
     * @param control reads the readiness of the tactic's choice
     * @return what rule 8 does with the mob
     */
    static Mark classify(MobSession session, Tactic best, PruningMode pruning, IntentControl control) {
        return judge(session, java.util.Objects.requireNonNull(best, "best"), pruning, control);
    }

    /** The rule itself; {@code found} is absent only through the overload without a tactic. */
    private static Mark judge(MobSession session, Tactic found, PruningMode pruning, IntentControl control) {
        var timing = session.profile().timing();
        if (timing.cadence() == DecisionCadence.STANDARD && pruning == PruningMode.OFF) {
            return Mark.INACTIVE;
        }
        if (!session.captureNoted() || session.liveSignature() == session.captureSignature()) {
            return Mark.UNCHANGED;
        }
        var veto = SituationRules.veto(found != null && sustain(session, found), session.probeHealth(),
                found != null && outOfReach(session, found),
                found == null ? ReadyIn.in(0) : readiness(session, found, control));
        return veto.isPresent() ? Mark.VETO : Mark.AGED;
    }

    private static boolean sustain(MobSession session, Tactic tactic) {
        var choice = tactic.choice().map(id -> session.profile().choices().get(id)).orElse(null);
        if (choice == null || !choice.authored() || choice.typedPurpose().primary() != SkillRole.SUSTAIN) {
            return false;
        }
        var band = session.probeBand();
        if (band.isEmpty() || band.get() == DistanceBand.BEYOND) {
            return false;
        }
        var signals = session.tickSignals();
        var visible = TickSignals.bit(signals, TickSignals.HAS_TARGET)
                && !TickSignals.bit(signals, TickSignals.TARGET_MISMATCH);
        return visible || session.lastTargetId().isPresent();
    }

    private static boolean outOfReach(MobSession session, Tactic tactic) {
        if (tactic.movement() != Movement.HOLD && tactic.movement() != Movement.RETREAT) {
            return false;
        }
        var choice = tactic.choice().map(id -> session.profile().choices().get(id)).orElse(null);
        if (choice == null || !choice.authored()) {
            return false;
        }
        var delivery = choice.typedPurpose().delivery();
        var band = session.probeBand().orElse(DistanceBand.BEYOND);
        if (delivery == Delivery.MELEE) {
            return band == DistanceBand.RANGED || band == DistanceBand.BEYOND;
        }
        return ranged(delivery) && band == DistanceBand.BEYOND;
    }

    private static boolean ranged(Delivery delivery) {
        return delivery == Delivery.PROJECTILE || delivery == Delivery.HITSCAN
                || delivery == Delivery.AREA_AT_TARGET || delivery == Delivery.CONE;
    }

    private static ReadyIn readiness(MobSession session, Tactic tactic, IntentControl control) {
        var id = tactic.choice();
        if (id.isEmpty()) {
            return ReadyIn.in(0);
        }
        var choice = session.profile().choices().get(id.get());
        if (choice == null || choice.kind() == ChoiceKind.VANILLA_MELEE) {
            return ReadyIn.in(0);
        }
        return control.readyIn(session.key(), choice);
    }
}
