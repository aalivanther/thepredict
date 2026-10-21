package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ContingencyPolicy;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.IntentPolicy;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.TacticalIntent;

import java.util.Objects;
import java.util.Optional;

/**
 * The reactive cycle of one probed session, every 2 ticks (M10.4): contingencies, alternatives, the cast and the
 * fallback chain, without waiting for the network. Package-private; server thread only.
 *
 * <p>It only reads the probe the coordinator already made: no new probe, capture or path (rule C12). In order:
 * an intent whose target changed since the last capture, or that expired, is no longer valid (an unconsumed choice
 * records {@code EXPIRED}); a valid Jev or alternative intent fires the first contingency whose situation holds,
 * once, as an {@code ALTERNATIVE} intent that also marks context pending, or moves to its next alternative when its
 * tactic is no longer eligible and its choice was not cast (a local intent in that case is planned again); without
 * a valid Jev or alternative intent the fallback chain decides, and re-arms or replans a local intent as soon as a
 * choice is ready (M10, real-time readiness). This cycle does not cast: it recalibrates the ready tick, and the ready
 * step casts outside the work budget (M19.4). A session held by an intent of the debug command only keeps its view
 * until the intent expires, and then drops to level 4 (M12.3). An exception escapes to {@code SessionUpkeep}, which
 * drops only that session to level 4.
 */
final class ReactiveLayer {
    /** Ticks between two reactive cycles of one session (M10.4). */
    static final int CYCLE_TICKS = 2;

    private final EncounterRegistry encounters;
    private final Agenda agenda;
    private final IntentControl control;
    private final FallbackStep fallback;

    ReactiveLayer(EncounterRegistry encounters, Agenda agenda, IntentControl control, FallbackStep fallback) {
        this.encounters = Objects.requireNonNull(encounters, "encounters");
        this.agenda = Objects.requireNonNull(agenda, "agenda");
        this.control = Objects.requireNonNull(control, "control");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    /** Runs the cycle of one active session with the probe of this tick. */
    void react(MobSession session, MobProbe probe, long tick, long nowNanos) {
        if (session.debugIntent()) {
            debug(session, tick, nowNanos);
            return;
        }
        control.showIntent(session, nowNanos);
        session.noteLiveSignature(LiveSignature.of(session, probe));
        control.extend(session, nowNanos);
        control.observe(session, probe, tick);
        var sameTarget = probe.targetId().equals(session.lastTargetId());
        var valid = session.intent().filter(intent -> sameTarget && !intent.expired(nowNanos));
        if (valid.isEmpty()) {
            control.expireChoice(session, tick, nowNanos);
        } else if (answered(valid.get())) {
            valid = steer(session, valid.get(), probe, tick);
        } else if (!stillEligible(session, valid.get())) {
            valid = Optional.empty();
        }
        if (valid.filter(ReactiveLayer::answered).isEmpty()) {
            fallback.apply(session, probe.worldId(), valid.map(TacticalIntent::level).orElse(ControlLevel.BASE),
                    sameTarget && probe.targetId().isPresent(), tick, nowNanos);
        }
        control.recalibrate(session, tick);
        control.settleExploit(session, tick);
        control.evaluateStance(session, tick);
    }

    /**
     * Runs a session held by the debug command: while its intent lasts it keeps the view and the ready step tries only
     * that choice; once it expires the session drops to level 4 and the chain takes over on the next cycle (M12.3).
     */
    private void debug(MobSession session, long tick, long nowNanos) {
        if (control.heldByDebug(session, nowNanos)) {
            control.showIntent(session, nowNanos);
            control.recalibrate(session, tick);
        } else {
            control.endDebug(session, tick);
        }
    }

    /**
     * Fires a contingency or moves to the next alternative of a valid Jev or alternative intent.
     *
     * @return the intent that is valid afterwards, empty when the tactic is no longer eligible and no alternative is
     */
    private Optional<TacticalIntent> steer(MobSession session, TacticalIntent intent, MobProbe probe, long tick) {
        var eligible = IntentControl.eligible(session);
        var fired = ContingencyPolicy.fire(intent, probe, eligible, session.profile(), tick);
        if (fired.isPresent()) {
            control.applyContingency(session, fired.get(), tick);
            session.encounter().flatMap(encounters::find).ifPresent(encounter -> agenda.markContextPending(encounter, tick));
            return fired;
        }
        if (stillEligible(session, intent)) {
            return Optional.of(intent);
        }
        var alternative = IntentPolicy.nextAlternative(intent, eligible, session.profile(), tick);
        alternative.ifPresent(next -> control.apply(session, next, tick));
        return alternative;
    }

    /** A tactic whose choice was cast keeps its movement; otherwise it must still be eligible in the last capture. */
    private static boolean stillEligible(MobSession session, TacticalIntent intent) {
        return intent.skillConsumed() || IntentControl.eligible(session).contains(intent.tactic());
    }

    private static boolean answered(TacticalIntent intent) {
        return intent.level() == ControlLevel.JEV || intent.level() == ControlLevel.ALTERNATIVE;
    }
}
