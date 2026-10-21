package akyrion.thepredict;

import akyrion.thepredict.domain.CandidateIntent;
import akyrion.thepredict.domain.CandidateSet;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DifficultyPolicy;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PressureLevers;
import akyrion.thepredict.domain.ReactionMask;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.engine.FightClock;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Local cost of the difficulty path (M22). The numbers are what this machine measured. Nothing leaves the machine.
 */
public final class M22Measurements {
    private M22Measurements() {
    }

    /**
     * Appends the Difficulty (M22) table for 20, 100 and 200 sessions.
     *
     * @param report the measurement report
     */
    public static void write(MeasurementReport report) {
        report.line("");
        report.line("## Difficulty (M22)");
        report.line("");
        report.line("| Sesiones | p50 / p95 / p99 / max µs por tick |");
        report.line("|---|---|");
        for (var sessions : new int[] {20, 100, 200}) {
            report.line("| " + sessions + " | " + MeasurementReport.micros(sample(sessions)) + " |");
        }
        report.line("");
        report.line("Una pasada es un `dealt`, un paso del lazo rápido y un `localPick`. "
                + "`adjustIntervalTicks` 100, `learningGain` 0,25, `toleranceRatio` 0,20.");
    }

    private static long[] sample(int sessions) {
        var clocks = new FightClock[sessions];
        var target = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
        for (var index = 0; index < sessions; index++) {
            clocks[index] = new FightClock();
        }
        var high = new CandidateIntent(new TacticId("high"), Movement.APPROACH, Optional.empty(), 800, 0,
                DamageTier.HIGH);
        var plain = new CandidateIntent(new TacticId("plain"), Movement.HOLD, Optional.empty(), 700, 0);
        var set = new CandidateSet(0L, 1, List.of(high, plain), Map.of(), List.of(high.tactic(), plain.tactic()));
        var levers = new PressureLevers(0, ReactionMask.ALL, 0, true, 150);
        var state = new DifficultyPolicy.Fast(8, 0, 0);
        var samples = new long[64];
        for (var sample = 0; sample < samples.length; sample++) {
            var started = System.nanoTime();
            for (var index = 0; index < sessions; index++) {
                clocks[index].dealt(sample, target, target, 0.01, 0.5);
                state = DifficultyPolicy.fast(state, 60.0, 60.0, 0.20, false, false, 0, 8);
                set.localPick(levers);
            }
            samples[sample] = System.nanoTime() - started;
        }
        return samples;
    }
}
