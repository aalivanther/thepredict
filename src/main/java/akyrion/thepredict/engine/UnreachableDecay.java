package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.SessionState;
import akyrion.thepredict.domain.ThreatPolicy;

import java.util.List;
import java.util.Objects;

/**
 * The unreachable-target fact of M11.2, run once per evaluation of an encounter after its capture. Package-private;
 * server thread only.
 *
 * <p>A member's target is unreachable at a decision only when the navigator follows a live path that does not reach
 * it ({@code hasPath} and not {@code pathReachesTarget}, read with {@link MobControl#progress} without computing a
 * path). Without a path nothing is subtracted, whatever the last known value says, so the decay is the same with or
 * without Jev and at every control level (plan §12). The facts of the capture filter first, without a reading: a
 * capture always reads that same current path first, so a live path that falls short is always captured as
 * unreachable. Only the second decision in a row with the same target and generation, each after a new capture,
 * removes threat: {@code unreachableDecayPerSecond} times the seconds since the previous one. A decision whose capture
 * did not read the member, a reachable or unknown target, another target, a new generation or a weight of zero
 * forgets the mark; a weight of zero also skips the reading.
 */
final class UnreachableDecay {
    private final ThreatChanges changes;
    private final ThreatMetrics metrics;

    UnreachableDecay(ThreatChanges changes, ThreatMetrics metrics) {
        this.changes = Objects.requireNonNull(changes, "changes");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /** Applies the decay of every member of an evaluated encounter; a member that throws only loses its mark. */
    int evaluated(Encounter encounter, long tick, MobControl control) {
        encounter.threat().keepMembers(encounter);
        var decays = 0;
        for (var member : List.copyOf(encounter.memberSessions())) {
            try {
                if (decay(encounter, member, tick, control)) {
                    decays++;
                }
            } catch (RuntimeException exception) {
                encounter.threat().forgetUnreachable(member.key().entityId());
                metrics.failures++;
            }
        }
        return decays;
    }

    private boolean decay(Encounter encounter, MobSession member, long tick, MobControl control) {
        var target = member.lastTargetId();
        var facts = member.lastMobFacts();
        var weights = member.profile().threat();
        var windows = encounter.threat();
        var fresh = facts.isPresent() && !windows.sameCapture(member.key().entityId(), facts.get());
        if (member.state() != SessionState.ACTIVE || target.isEmpty() || !fresh
                || weights.unreachableDecayPerSecond() == 0.0 || !unreachable(facts.get(), member.key(), control)) {
            windows.forgetUnreachable(member.key().entityId());
            return false;
        }
        var previous = windows.markUnreachable(member.key(), target.get(), facts.get(), tick);
        if (previous.isEmpty()) {
            return false;
        }
        var change = ThreatPolicy.unreachableDecay(Math.max(0L, tick - previous.getAsLong()), weights);
        if (change == 0.0 || !changes.apply(encounter, member.key(), target.get(), change, "UNREACHABLE", tick)) {
            return false;
        }
        metrics.decays++;
        metrics.pointsRemoved -= change;
        return true;
    }

    private static boolean unreachable(MobFacts facts, MobKey key, MobControl control) {
        var relation = facts.target();
        if (relation.isEmpty() || relation.get().reachable()) {
            return false;
        }
        var progress = control.progress(key);
        return progress.hasPath() && !progress.pathReachesTarget();
    }
}
