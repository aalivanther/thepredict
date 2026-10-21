package akyrion.thepredict.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fairness of the local A/B harness of M13.5 (plan §12): both arms see the same fight, the arm without Jev costs
 * nothing, no arm gains by duplicated casts, the runs repeat exactly, and the pipeline follows a scripted Jev within one
 * heartbeat plus the answer latency after each change of the fight. It says nothing about the quality of the real
 * model; that is the scenario bank and the server A/B.
 */
class TacticalExecutionABTest {
    private static final int REACTION_TICKS = 20 + TacticalAB.LATENCY_TICKS + 2;

    /**
     * Same fight, no duplicated cast and no cost without Jev, with the timing of the golden references and with the
     * delivered timing ({@code any_ready}, {@code reactions}, {@code reactive}; PZ-33).
     */
    @ParameterizedTest
    @EnumSource(TacticalAB.Scenario.class)
    void bothArmsSeeTheSameFightAndNeitherGainsByDuplicatedCasts(TacticalAB.Scenario scenario) {
        for (var timing : java.util.List.of(TacticalAB.DEFAULTS, TacticalAB.DELIVERED)) {
            var results = TacticalAB.repeat(scenario, 10, 0.85, timing);
            var without = results.get(TacticalAB.Arm.WITHOUT_JEV);
            var with = results.get(TacticalAB.Arm.WITH_JEV);
            assertEquals(10, without.size());
            assertEquals(10, with.size());
            for (var index = 0; index < 10; index++) {
                assertEquals(without.get(index).scriptHash(), with.get(index).scriptHash(), "same fight for both arms");
                assertEquals(0, without.get(index).duplicateCasts(), timing.cadence() + " " + without.get(index));
                assertEquals(0, with.get(index).duplicateCasts(), timing.cadence() + " " + with.get(index));
                assertEquals(0, without.get(index).requests());
                assertEquals(0.0, without.get(index).costUsd());
                assertTrue(with.get(index).requests() > 0);
            }
        }
    }

    @Test
    void withTheConfigDefaultsAJevThatRenewsTheSameTacticCastsAtTheLocalRate() {
        for (var timing : java.util.List.of(TacticalAB.DEFAULTS, TacticalAB.DELIVERED)) {
            var local = 0L;
            var jev = 0L;
            for (var seed = 1; seed <= 10; seed++) {
                local += TacticalAB.run(TacticalAB.Scenario.MELEE_DUEL, TacticalAB.Arm.WITHOUT_JEV, seed, 1.0, timing)
                        .casts();
                var renewed = TacticalAB.run(TacticalAB.Scenario.MELEE_DUEL, TacticalAB.Arm.WITH_JEV, seed, 1.0, timing);
                jev += renewed.casts();
                assertEquals(0, renewed.duplicateCasts());
            }
            assertTrue(Math.abs(jev - local) <= local / 5, timing.cadence() + ": Jev " + jev + " against local " + local
                    + " casts (DT-28)");
        }
    }

    @Test
    void aRunRepeatsExactlyWithTheSameSeed() {
        for (var scenario : TacticalAB.Scenario.values()) {
            for (var arm : TacticalAB.Arm.values()) {
                assertEquals(TacticalAB.run(scenario, arm, 7, 0.85), TacticalAB.run(scenario, arm, 7, 0.85));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(TacticalAB.Scenario.class)
    void withAnAlwaysAcceptableJevThePipelineLosesAtMostOneReactionPerChangeOfTheFight(TacticalAB.Scenario scenario) {
        for (var seed = 1; seed <= 5; seed++) {
            var script = TacticalAB.script(scenario, seed);
            var changes = 0;
            for (var tick = 1; tick < script.size(); tick++) {
                if (!script.get(tick).acceptable().equals(script.get(tick - 1).acceptable())) {
                    changes++;
                }
            }
            var result = TacticalAB.run(scenario, TacticalAB.Arm.WITH_JEV, seed, 1.0);
            var floor = 1.0 - (changes + 1.0) * REACTION_TICKS / TacticalAB.TICKS;
            assertTrue(result.usefulShare() >= floor, result + " below " + floor);
        }
    }

    @Test
    void theOutcomeWindowsMeasureEffectAndHitRateWithTheSameScriptedDamageForBothArms() {
        var results = TacticalAB.repeat(TacticalAB.Scenario.MELEE_DUEL, 3, 1.0);
        for (var arm : TacticalAB.Arm.values()) {
            for (var result : results.get(arm)) {
                assertTrue(result.hitRate() > 0.0 && result.hitRate() <= 1.0, arm + ": " + result.hitRate());
                assertTrue(Double.isFinite(result.effect()), arm.name());
            }
        }
        assertEquals(TacticalAB.run(TacticalAB.Scenario.SHIELD, TacticalAB.Arm.WITH_JEV, 3, 0.85).effect(),
                TacticalAB.run(TacticalAB.Scenario.SHIELD, TacticalAB.Arm.WITH_JEV, 3, 0.85).effect());
    }
}
