package akyrion.thepredict.engine;

import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.SituationSignature;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.ZoneFact;
import akyrion.thepredict.domain.ZoneMode;

/**
 * The seventeen-bit situation of one session (M20.6). The same function runs at dispatch and again when an answer or
 * a reactive visit is judged, so a later capture does not replace the signature an intent was adopted with.
 */
final class LiveSignature {
    private LiveSignature() {
    }

    /** Signature from the probe and signals already stored on the session. */
    static int of(MobSession session) {
        return pack(session, session.probeHealth(), session.probeBand().orElse(DistanceBand.BEYOND));
    }

    /** Signature from the probe of this reactive visit and the signals already stored. */
    static int of(MobSession session, MobProbe probe) {
        return pack(session, probe.health(), probe.targetDistance().orElse(DistanceBand.BEYOND));
    }

    private static int pack(MobSession session, HealthBand self, DistanceBand distance) {
        var signals = session.tickSignals();
        var visible = TickSignals.bit(signals, TickSignals.HAS_TARGET)
                && !TickSignals.bit(signals, TickSignals.TARGET_MISMATCH);
        var full = session.zoneMode() == ZoneMode.FULL;
        var facts = full ? session.lastMobFacts() : java.util.Optional.<akyrion.thepredict.domain.MobFacts>empty();
        var edge = facts.isPresent() && facts.get().zoneFacts().contains(ZoneFact.EDGE_BEHIND);
        var retreat = facts.isPresent() && facts.get().zoneFacts().contains(ZoneFact.NO_SAFE_RETREAT);
        var bits = SituationSignature.patternBits(session.patternMode(), session.profile().patterns().facts(),
                session.patternMask());
        return SituationSignature.pack(distance, self, target(session, signals),
                TickSignals.bit(signals, TickSignals.TARGET_BLOCKING),
                TickSignals.bit(signals, TickSignals.TARGET_SHOT_READY),
                TickSignals.bit(signals, TickSignals.TARGET_CONSUMING),
                TickSignals.bit(signals, TickSignals.TARGET_ATTACK_RECHARGING),
                visible, visible && distance != DistanceBand.BEYOND, false, readyMask(session), edge, retreat, bits);
    }

    private static HealthBand target(MobSession session, long signals) {
        return band(TickSignals.targetHealthPercent(signals) / 100.0, session.profile().health());
    }

    private static HealthBand band(double ratio, HealthThresholds thresholds) {
        if (ratio <= thresholds.criticalRatio()) {
            return HealthBand.CRITICAL;
        }
        return ratio <= thresholds.woundedRatio() ? HealthBand.WOUNDED : HealthBand.HEALTHY;
    }

    private static int readyMask(MobSession session) {
        var set = session.candidates();
        if (set.isEmpty()) {
            return 0;
        }
        var horizon = session.profile().timing().readyHorizonTicks();
        var candidates = set.get().candidates();
        var limit = Math.min(4, candidates.size());
        var mask = 0;
        for (var index = 0; index < limit; index++) {
            if (candidates.get(index).readyInTicks() <= horizon) {
                mask |= 1 << index;
            }
        }
        return mask;
    }
}
