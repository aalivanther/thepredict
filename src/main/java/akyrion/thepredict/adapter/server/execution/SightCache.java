package akyrion.thepredict.adapter.server.execution;

import akyrion.thepredict.engine.ServerClock;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One line of sight per mob and server tick (M19.5, rule C12). The engine reads the sight of a candidate
 * ({@code seesTarget}) and the cast reads it again for a choice that needs it; both land here, so the second read of
 * the same tick costs a map lookup instead of a ray. Server thread only, like the adapter that owns it.
 *
 * <p>The answer is keyed by the caster alone because both reads aim at the capture's target of that tick. Entries are
 * reused across ticks, one per mob ever seen; the map is emptied when it holds more than {@value #MAX_MOBS} mobs, so it
 * never grows without bound (A5). Paper's {@code LivingEntity#hasLineOfSight} documents no cache of its own.
 */
final class SightCache implements LineOfSight {
    /** Most mobs remembered before the map starts again. */
    static final int MAX_MOBS = 4096;

    private final ServerClock clock;
    private final Map<UUID, Answer> answers = new HashMap<>();

    /**
     * Creates the cache.
     *
     * @param clock clock whose {@code currentTick} bounds every remembered answer
     */
    SightCache(ServerClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean sees(UUID mobId, LivingEntity caster, LivingEntity target) {
        var tick = clock.currentTick();
        var answer = answers.get(mobId);
        if (answer != null && answer.tick == tick) {
            return answer.seen;
        }
        var seen = caster.hasLineOfSight(target);
        if (answer == null) {
            if (answers.size() >= MAX_MOBS) {
                answers.clear();
            }
            answer = new Answer();
            answers.put(mobId, answer);
        }
        answer.tick = tick;
        answer.seen = seen;
        return seen;
    }

    /** Remembered mobs, for the tests of the cap. */
    int size() {
        return answers.size();
    }

    private static final class Answer {
        private long tick;
        private boolean seen;
    }
}
