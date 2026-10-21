package akyrion.thepredict.engine.duel;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The invariants of the local duel simulator of M18.7, never a claim of superiority (plan §12): arm D is symmetric,
 * no cast is duplicated within an intent in any arm and mode, a ThePredict duelist forced to level 4 from the first tick
 * plays exactly as the classic one, and every round ends within {@value DuelSimulator#MAX_TICKS} ticks.
 */
class DuelSimulatorTest {
    private static final List<DuelSimulator.Arm> ENGINE_ARMS = List.of(DuelSimulator.Arm.A, DuelSimulator.Arm.B,
            DuelSimulator.Arm.C);

    @Test
    void armDIsSymmetricOverTwoHundredSeeds() {
        var d = DuelMatrix.run(List.of(DuelSimulator.Arm.D), 200, new DuelSimulator.Mode.None())
                .get(DuelSimulator.Arm.D);
        assertEquals(200, d.rounds());
        assertTrue(Math.abs(d.redScore() - 0.5) <= 0.10, "red score of arm D: " + d.redScore());
    }

    @Test
    void noCastIsDuplicatedWithinAnIntentInAnyArmOrMode() {
        for (var mode : List.of(new DuelSimulator.Mode.None(), new DuelSimulator.Mode.Oracle(0.85, 3L))) {
            var matrix = DuelMatrix.run(ENGINE_ARMS, 12, mode);
            for (var summary : matrix.values()) {
                assertEquals(0, summary.duplicateCasts(), summary.arm() + " " + mode);
            }
        }
    }

    @Test
    void aThePredictDuelistForcedToLevelFourPlaysExactlyAsTheClassicOne() {
        var forced = new DuelSimulator(new DuelSimulator.Mode.None());
        var classic = new DuelSimulator(new DuelSimulator.Mode.None());
        for (var seed = 0; seed < 20; seed++) {
            var swapped = (seed / 2) % 2 == 1;
            var base = forced.round(DuelSimulator.Arm.A, seed, swapped, new DuelSimulator.Options(true, -1));
            var reference = classic.round(DuelSimulator.Arm.D, seed, swapped, DuelSimulator.Options.PLAIN);
            assertEquals(reference.redSequence(), base.redSequence(), "seed " + seed);
            assertEquals(reference.winner(), base.winner(), "seed " + seed);
            assertEquals(reference.ticks(), base.ticks(), "seed " + seed);
            assertEquals(base.ticks(), base.levelTicks().get(akyrion.thepredict.domain.ControlLevel.BASE),
                    "red never leaves level 4");
        }
    }

    @Test
    void aDuelistThatLosesItsTargetReachesLevelFourLateAndTheDelayIsMeasured() {
        var simulator = new DuelSimulator(new DuelSimulator.Mode.None());
        var measured = 0;
        for (var seed = 0; seed < 10; seed++) {
            var result = simulator.round(DuelSimulator.Arm.A, seed, false, new DuelSimulator.Options(false, 100));
            if (result.baseDelayTicks() >= 0) {
                measured++;
                assertTrue(result.baseDelayTicks() <= 200, "seed " + seed + ": " + result.baseDelayTicks());
            }
        }
        assertTrue(measured > 0);
    }

    @Test
    void everyRoundEndsWithinTheTimeoutInEveryArm() {
        for (var mode : List.of(new DuelSimulator.Mode.None(), new DuelSimulator.Mode.Oracle(0.85, 5L))) {
            var simulator = new DuelSimulator(mode);
            for (var arm : DuelSimulator.Arm.values()) {
                for (var seed = 0; seed < 6; seed++) {
                    var result = simulator.round(arm, seed, seed % 2 == 1, DuelSimulator.Options.PLAIN);
                    assertTrue(result.ticks() > 0 && result.ticks() <= DuelSimulator.MAX_TICKS, arm + " " + seed);
                    assertTrue(result.winner() != DuelResult.Winner.DRAW || result.ticks() == DuelSimulator.MAX_TICKS
                            || result.redDealt() > 0 && result.blueDealt() > 0, "a draw is a timeout or a double "
                            + "death: " + arm + " " + seed);
                }
            }
        }
    }
}
