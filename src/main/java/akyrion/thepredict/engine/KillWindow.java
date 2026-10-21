package akyrion.thepredict.engine;

import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TickSignals;

import java.util.Optional;

/**
 * The kill window (fase de efectividad): while the target is one own hit from death, the mob does not turn its back
 * to it. Retreating then only hands the last exchange to the rival, who keeps hitting a mob that runs at its own speed
 * while the finishing hit stays out of reach. One hit is the largest share of the target's maximum health a single own
 * hit dealt to this target ({@link OwnImpacts#strongestShare}); the target's health is the share the signals read,
 * absorption included, so a target behind a fresh shield is not in the window. Pure apart from the session it reads;
 * no allocation.
 */
final class KillWindow {
    private KillWindow() {
    }

    /**
     * Whether the target of the session is within one own hit of death on the signals of this tick.
     *
     * @param session the mob
     * @return whether the window is open
     */
    static boolean open(MobSession session) {
        var signals = session.tickSignals();
        if (!TickSignals.bit(signals, TickSignals.VALID) || !TickSignals.bit(signals, TickSignals.HAS_TARGET)) {
            return false;
        }
        var share = session.ownImpacts().strongestShare();
        return share > 0.0 && TickSignals.targetHealthPercent(signals) <= share * 100.0;
    }

    /**
     * Whether a tactic keeps the mob on its target: any movement but a retreat.
     *
     * @param tactic a tactic
     * @return whether it stays
     */
    static boolean stays(Tactic tactic) {
        return tactic.movement() != Movement.RETREAT;
    }

    /**
     * Whether the current intent of the session retreats.
     *
     * @param session the mob
     * @return whether its movement is a retreat
     */
    static boolean retreating(MobSession session) {
        var intent = session.intent().orElse(null);
        return intent != null && intent.movement() == Movement.RETREAT;
    }

    /**
     * The tactic to plan inside the window: the one chosen if it stays, otherwise the best one that does.
     *
     * @param chosen the tactic the chain chose
     * @param staying the best tactic of the chain without a retreat
     * @return the tactic that stays, or the chosen one when none does
     */
    static Optional<Tactic> press(Optional<Tactic> chosen, Optional<Tactic> staying) {
        if (chosen.isEmpty() || stays(chosen.get())) {
            return chosen;
        }
        return staying.isPresent() ? staying : chosen;
    }
}
