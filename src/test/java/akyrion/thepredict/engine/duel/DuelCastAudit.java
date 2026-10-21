package akyrion.thepredict.engine.duel;

import akyrion.thepredict.domain.IntentView;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cast cadence of one M19.9 configuration (E2). Counts, for ThePredict duelists only, starts during a wind-up, ticks a
 * ready skill waited with no guard, and casts against the ticks each skill spent inside its reach. Deterministic: no
 * clock of the machine.
 */
final class DuelCastAudit {
    private final Map<Due, Long> due = new HashMap<>();
    private final Map<UUID, Integer> range = new HashMap<>();
    final long[] casts = new long[DuelSkill.values().length];
    final long[] inRangeTicks = new long[DuelSkill.values().length];
    long preparationStarts;
    long lateTicks;

    /** Drops the readiness marks of the previous round. Totals stay. */
    void beginRound() {
        due.clear();
        range.clear();
    }

    /** Skills that are ready and unguarded before the engine ticks, and the reach mask of each ThePredict duelist. */
    void before(DuelWorld world, DuelSimulator.Arm arm, long tick) {
        note(world, world.red, arm.redTp, tick);
        note(world, world.blue, arm.blueTp, tick);
    }

    /** Drops a duelist the engine does not control and, otherwise, adds the reach ticks sampled before the tick. */
    void afterControl(DuelWorld.Body body, boolean controlled) {
        if (!controlled) {
            clear(body.id);
            range.remove(body.id);
            return;
        }
        var mask = range.remove(body.id);
        if (mask == null) {
            return;
        }
        for (var skill : DuelSkill.values()) {
            if ((mask & (1 << skill.ordinal())) != 0) {
                inRangeTicks[skill.ordinal()]++;
            }
        }
    }

    /** A cast the engine actually started. */
    void onCast(DuelWorld.Body body, DuelSkill skill, long tick) {
        casts[skill.ordinal()]++;
        due.keySet().removeIf(key -> key.id.equals(body.id));
    }

    /** Ticks a skill stayed ready and unguarded without being cast. */
    void finishTick(long tick) {
        for (var since : due.values()) {
            if (since < tick) {
                lateTicks++;
            }
        }
    }

    private void note(DuelWorld world, DuelWorld.Body body, boolean thepredict, long tick) {
        if (!thepredict) {
            clear(body.id);
            return;
        }
        var mask = 0;
        for (var skill : DuelSkill.values()) {
            if (skill.withinBlocks == 0 || world.distance() < skill.withinBlocks) {
                mask |= 1 << skill.ordinal();
            }
            var key = new Due(body.id, skill);
            if (unguarded(world, body, skill, tick)) {
                due.putIfAbsent(key, tick);
            } else {
                due.remove(key);
            }
        }
        range.put(body.id, mask);
    }

    private static boolean unguarded(DuelWorld world, DuelWorld.Body body, DuelSkill skill, long tick) {
        if (!body.ready(skill, tick) || world.windingUp(body, tick) || !world.conditions(body, skill)) {
            return false;
        }
        return skill.damage <= 0 || tick >= world.rival(body).invulnerableUntil;
    }

    private void clear(UUID id) {
        due.keySet().removeIf(key -> key.id.equals(id));
        range.remove(id);
    }

    /** Whether the published intent still drives the duelist. */
    static boolean controls(IntentView view, long nanos) {
        return view.controls(nanos);
    }

    private record Due(UUID id, DuelSkill skill) {
    }
}
