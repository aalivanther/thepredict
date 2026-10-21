package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActionStance;
import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.PoiseMeter;
import akyrion.thepredict.domain.PoiseSettings;
import akyrion.thepredict.domain.StanceMachine;

import java.util.UUID;

/**
 * Fills profile poise from damage taken and from a blocked heavy hit (M23.7). Without a {@code Poise} section it does
 * not call {@link PoiseMeter}. A break interrupts the open tell, which, as every tell that does not complete, leaves
 * the cooldown of its choice besides the stagger (A11).
 */
final class PoiseWatch {
    private PoiseWatch() {
    }

    /**
     * Damage taken from an attacker. The open window is fed by the caller. A rival fills poise. The tell's target
     * marks {@code punished} during recovery or stagger.
     *
     * @param session the member
     * @param attacker causing entity
     * @param fraction final damage over maximum health
     * @param tick server tick
     * @param rival whether the attacker is another participant of this session's encounter
     * @param actions cues on a break
     * @param step length at least 5, owned by the caller
     */
    static void onDamage(MobSession session, UUID attacker, double fraction, long tick, boolean rival,
                         CombatActions actions, long[] step) {
        var action = session.actionStance();
        var punish = session.punishTarget();
        if (attacker != null && attacker.equals(punish)
                && (action == ActionStance.RECOVERY || action == ActionStance.STAGGERED)) {
            session.punished(true);
        }
        if (attacker == null || !rival) {
            return;
        }
        var settings = session.profile().poise().orElse(null);
        if (settings == null) {
            return;
        }
        absorb(session, tick, fraction, action == ActionStance.WINDUP, settings, actions, step);
    }

    /**
     * A blocked melee hit of this mob. Only a heavy or severe cast adds {@code BlockedHitPoise}.
     *
     * @param session the member
     * @param tick server tick
     * @param actions cues on a break
     * @param step length at least 5, owned by the caller
     */
    static void onBlocked(MobSession session, long tick, CombatActions actions, long[] step) {
        if (session.lastDanger() < DangerClass.HEAVY.ordinal()) {
            return;
        }
        var settings = session.profile().poise().orElse(null);
        if (settings == null) {
            return;
        }
        absorb(session, tick, settings.blockedHitPoise(), false, settings, actions, step);
    }

    private static void absorb(MobSession session, long tick, double incoming, boolean windup, PoiseSettings settings,
                               CombatActions actions, long[] step) {
        double stored = PoiseMeter.hit(session.poiseDamage(), session.lastPoiseHit(), tick, incoming, windup,
                session.poiseImmuneUntil(), settings);
        if (tick < session.poiseImmuneUntil()) {
            return;
        }
        session.poiseDamage(stored);
        session.lastPoiseHit(tick);
        if (!PoiseMeter.broken(stored, settings)) {
            publish(session, stored, settings, false);
            return;
        }
        boolean castInFlight = tick < session.busyUntilTick();
        session.poiseDamage(0.0);
        session.poiseImmuneUntil(tick + settings.immunityTicks());
        StanceClock.apply(session, tick, session.baseStance().ordinal(), false, true, false, StanceMachine.EVENT_POISE,
                settings.staggerTicks(), 0, actions, step);
        session.nextStanceTick(session.actionUntilTick());
        if (castInFlight) {
            session.inFlightInterrupt().interrupt();
        }
        if (session.tellOpen()) {
            session.tellResults().enqueue(session, tick, akyrion.thepredict.domain.TelegraphEnd.INTERRUPTED);
            GroupTellClose.apply(session, akyrion.thepredict.domain.TelegraphEnd.INTERRUPTED, tick);
            session.tellInterrupt().interrupt();
            session.noteWhiff(session.tellChoice(), tick + session.profile().telegraph().whiffCooldownTicks());
            session.closeTell();
        }
        publish(session, 0.0, settings, true);
    }

    private static void publish(MobSession session, double stored, PoiseSettings settings, boolean broke) {
        int percent = settings.max() <= 0.0 ? 0 : (int) Math.round(100.0 * Math.min(stored, settings.max()) / settings.max());
        int shown = session.poiseShown();
        if (broke || shown < 0 || Math.abs(percent - shown) >= 5) {
            session.poiseShown(percent);
            session.publishStanceView();
        }
    }
}
