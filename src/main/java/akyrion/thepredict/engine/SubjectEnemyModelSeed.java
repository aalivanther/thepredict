package akyrion.thepredict.engine;

import akyrion.thepredict.domain.MemorySettings;
import akyrion.thepredict.domain.PatternSeed;
import akyrion.thepredict.domain.PlayerMemory;
import akyrion.thepredict.domain.PlayerMemoryMath;
import akyrion.thepredict.domain.StyleMemory;

import java.util.UUID;

/**
 * Seeds a new encounter from one player's style (M27.5). The cap is {@code seedMaxTrials} times forgetting.
 * Action prediction is not seeded.
 */
public final class SubjectEnemyModelSeed implements EnemyModelSeed {
    private final PlayerMemory memory;
    private final MemorySettings settings;
    private final long today;

    /**
     * A seed for one loaded record.
     *
     * @param memory the record
     * @param settings the limits
     * @param todayEpochDay the civil day
     */
    public SubjectEnemyModelSeed(PlayerMemory memory, MemorySettings settings, long todayEpochDay) {
        this.memory = memory;
        this.settings = settings;
        this.today = todayEpochDay;
    }

    @Override
    public PatternSeed seedFor(UUID participant) {
        if (memory.style().isEmpty()) {
            return PatternSeed.NONE;
        }
        return masses(memory.style().get());
    }

    private PatternSeed masses(StyleMemory style) {
        var tau = PlayerMemoryMath.tau(today - memory.lastEpochDay(), settings.halfLifeDays());
        var successes = new double[24];
        var failures = new double[24];
        for (var pattern = 0; pattern < 24; pattern++) {
            var success = style.success(pattern);
            var failure = style.failure(pattern);
            var total = success + failure;
            if (total < 2f) {
                continue;
            }
            var kept = Math.min(settings.seedMaxTrials(), total) * tau / total;
            successes[pattern] = success * kept;
            failures[pattern] = failure * kept;
        }
        return new PatternSeed(successes, failures);
    }
}
