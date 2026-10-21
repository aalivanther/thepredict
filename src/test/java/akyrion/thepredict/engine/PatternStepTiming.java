package akyrion.thepredict.engine;

import akyrion.thepredict.adapter.server.patterns.EnemyKinematicsReader;
import akyrion.thepredict.adapter.server.patterns.EnemyWatch;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.PatternSettings;

import java.util.Arrays;
import java.util.UUID;

/**
 * Percentiles of one pattern step, one kinematic read and one watch admission. The samples are wall time on this
 * machine. They are not an object size.
 */
public final class PatternStepTiming {
    private static final int SAMPLES = 200;

    private PatternStepTiming() {
    }

    /**
     * Six numbers: step p50, step p99, kinematic p50, kinematic p99, watch p50, watch p99, in nanoseconds.
     *
     * @return the percentiles
     */
    public static long[] sample() {
        var step = timeStep();
        var kinematic = timeKinematic();
        var watch = timeWatch();
        return new long[] {percentile(step, 0.50), percentile(step, 0.99), percentile(kinematic, 0.50),
                percentile(kinematic, 0.99), percentile(watch, 0.50), percentile(watch, 0.99)};
    }

    private static long[] timeStep() {
        var gate = new PatternModeGate();
        gate.offer(PatternMode.OBSERVE, false);
        var director = new PatternDirector(new PatternInbox(), new KinematicBook(), PatternSettings.DEFAULT, gate,
                System::nanoTime);
        var session = new MobSession(new MobKey(new UUID(3, 4), 1), EngineFixtures.profile("Guardian"));
        session.recordTarget(new UUID(5, 6), 4.0);
        session.tickSignals(akyrion.thepredict.domain.TickSignals.PRESENT);
        var samples = new long[SAMPLES];
        for (var index = 0; index < 40; index++) {
            director.visit(session, index);
        }
        for (var index = 0; index < SAMPLES; index++) {
            var start = System.nanoTime();
            director.visit(session, 100 + index);
            samples[index] = System.nanoTime() - start;
        }
        return samples;
    }

    private static long[] timeKinematic() {
        var reader = new EnemyKinematicsReader(1.0, 2.0, 90.0f, 0.0, false, false, 4.0, 2.0, true, 0.15);
        var samples = new long[SAMPLES];
        var word = 0L;
        for (var index = 0; index < 40; index++) {
            word = reader.read(word, 2);
        }
        for (var index = 0; index < SAMPLES; index++) {
            var start = System.nanoTime();
            word = reader.read(word, 2);
            samples[index] = System.nanoTime() - start;
        }
        if (word == Long.MIN_VALUE) {
            samples[0] = 1;
        }
        return samples;
    }

    private static long[] timeWatch() {
        var inbox = new PatternInbox();
        var watch = new EnemyWatch(inbox, () -> 1L);
        var known = new UUID(7, 8);
        var stranger = new UUID(9, 10);
        inbox.admit(known);
        var samples = new long[SAMPLES];
        for (var index = 0; index < 40; index++) {
            watch.consider(index % 2 == 0 ? known : stranger, index % 2 == 0, 1, index);
        }
        for (var index = 0; index < SAMPLES; index++) {
            var start = System.nanoTime();
            watch.consider(index % 2 == 0 ? known : stranger, index % 2 == 0, 1, index);
            samples[index] = System.nanoTime() - start;
        }
        return samples;
    }

    private static long percentile(long[] samples, double fraction) {
        var copy = Arrays.copyOf(samples, samples.length);
        Arrays.sort(copy);
        var index = (int) Math.ceil(fraction * copy.length) - 1;
        return copy[Math.max(0, index)];
    }
}
