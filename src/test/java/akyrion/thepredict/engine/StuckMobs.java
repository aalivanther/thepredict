package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.FallbackReason;
import akyrion.thepredict.domain.MovementProgress;
import akyrion.thepredict.domain.ServiceState;
import akyrion.thepredict.domain.TacticId;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static akyrion.thepredict.engine.EngineFixtures.id;
import static akyrion.thepredict.engine.ExecutionFixture.nowAndContingency;
import static akyrion.thepredict.engine.ExecutionFixture.prefer;

/**
 * A mob that cannot make progress toward its target (pending of M10 for M13): how often the rule {@code NO_PROGRESS}
 * makes it alternate between its local plan and the base AI, without Jev and with Jev insisting on the same tactic.
 * The progress double never advances, as for a target on a pillar; the level is sampled every tick.
 */
final class StuckMobs {
    private StuckMobs() {
    }

    /** Level changes and ticks per level of one stuck mob over {@code ticks}. */
    record Alternation(int switchesToBase, int switchesFromBase, Map<ControlLevel, Integer> ticksAt,
                       long noProgressFallbacks, int requests, int signals, long casts) {
    }

    static Alternation withoutJev(int ticks) {
        var fixture = ExecutionFixture.starting(ServiceState.UNCONFIGURED);
        prepare(fixture);
        return sample(fixture, ticks, null);
    }

    static Alternation withJevInsisting(int ticks, TacticId insisted) {
        var fixture = new ExecutionFixture();
        fixture.ready();
        prepare(fixture);
        return sample(fixture, ticks, insisted);
    }

    private static void prepare(ExecutionFixture fixture) {
        fixture.open(1);
        fixture.target(1, 1);
        fixture.mobs.progress.put(id(1), new ArrayDeque<>(List.of(new MovementProgress(false, false, false,
                OptionalDouble.of(6.0)))));
    }

    private static Alternation sample(ExecutionFixture fixture, int ticks, TacticId insisted) {
        var ticksAt = new EnumMap<ControlLevel, Integer>(ControlLevel.class);
        var toBase = 0;
        var fromBase = 0;
        var previous = fixture.view(1).level();
        for (var tick = 0; tick < ticks; tick++) {
            if (insisted == null) {
                fixture.run(1);
            } else {
                fixture.runAnswering(1, nowAndContingency(prefer(insisted, 0.7, 0.9), prefer(insisted, 0.7, 0.9)));
            }
            var level = fixture.view(1).level();
            ticksAt.merge(level, 1, Integer::sum);
            if (level == ControlLevel.BASE && previous != ControlLevel.BASE) {
                toBase++;
            } else if (level != ControlLevel.BASE && previous == ControlLevel.BASE) {
                fromBase++;
            }
            previous = level;
        }
        return new Alternation(toBase, fromBase, Map.copyOf(ticksAt), fixture.count(FallbackReason.NO_PROGRESS),
                fixture.client.requests.size(), fixture.actions.signals(1).size(), fixture.actions.casts(1));
    }
}
