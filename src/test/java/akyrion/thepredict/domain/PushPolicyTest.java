package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cast order of revision 3 (errata E24-7). A medium lethal push opens nothing; a heavy one needs a real tell (A12). */
class PushPolicyTest {
    @Test
    void mediumBesideAVoidDefersAndOpensNothing() {
        var openings = 0;
        var telegraph = 0;
        for (var deferred = 0; deferred < 60; deferred++) {
            var step = ZoneCastOrder.step(true, ZoneMode.OBSERVE, false, DangerClass.MEDIUM, Feasibility.FEASIBLE,
                    DisplacementKind.KNOCKBACK, PushDanger.LETHAL, deferred, 60, 0, 40, true);
            if (step == ZoneCastOrder.Step.OPEN) {
                openings++;
                telegraph++;
            }
            assertEquals(ZoneCastOrder.Step.DEFER, step);
        }
        assertEquals(0, openings);
        assertEquals(0, telegraph);
        assertEquals(PushDecision.SKIP, PushPolicy.decide(true, DisplacementKind.KNOCKBACK, PushDanger.LETHAL,
                DangerClass.MEDIUM, Feasibility.FEASIBLE, 60, 60, false, ZoneMode.OBSERVE, true));
        assertEquals(ZoneCastOrder.Step.CAST, ZoneCastOrder.step(true, ZoneMode.OBSERVE, false, DangerClass.LIGHT,
                Feasibility.FEASIBLE, DisplacementKind.KNOCKBACK, PushDanger.NONE, 0, 60, 0, 40));
        var lifted = ZoneCastOrder.step(true, ZoneMode.GUARD, true, DangerClass.MEDIUM, Feasibility.INFEASIBLE,
                DisplacementKind.KNOCKBACK, PushDanger.NONE, 0, 60, 0, 40);
        assertEquals(ZoneCastOrder.Step.RETRY, ZoneCastOrder.step(true, ZoneMode.GUARD, false, DangerClass.MEDIUM,
                Feasibility.INFEASIBLE, DisplacementKind.KNOCKBACK, PushDanger.NONE, 0, 60, 0, 40));
        assertNotEquals(ZoneCastOrder.Step.RETRY, lifted);
        assertNotEquals(ZoneCastOrder.Step.DROP, lifted);
    }

    @Test
    void unknownDangerAllowsAndEntropyAllowsALethalPush() {
        assertEquals(PushDecision.ALLOW, PushPolicy.decide(true, DisplacementKind.KNOCKBACK, PushDanger.UNKNOWN,
                DangerClass.MEDIUM, Feasibility.UNKNOWN, 0, 60, false, ZoneMode.OBSERVE));
        assertEquals(PushDecision.ALLOW, PushPolicy.decide(true, DisplacementKind.KNOCKBACK, PushDanger.LETHAL,
                DangerClass.LIGHT, Feasibility.INFEASIBLE, 0, 60, true, ZoneMode.GUARD));
        assertEquals(PushDecision.ALLOW, PushPolicy.decide(true, DisplacementKind.KNOCKBACK, PushDanger.LETHAL,
                DangerClass.LIGHT, Feasibility.INFEASIBLE, 0, 60, true, ZoneMode.FULL));
        assertEquals(PushDecision.DEFER, PushPolicy.decide(true, DisplacementKind.KNOCKBACK, PushDanger.LETHAL,
                DangerClass.LIGHT, Feasibility.INFEASIBLE, 0, 60, true, ZoneMode.OBSERVE));
    }

    @Test
    void aLethalPushAgainstAPlayerNeedsATellThatShowsItsMinimum() {
        var off = tells(false, ShortWindUp.LEAD);
        var immediate = tells(true, ShortWindUp.IMMEDIATE);
        var enforced = TelegraphSettings.DEFAULT;
        var minimum = enforced.minimumTicks(DangerClass.SEVERE, TellAnswer.BLOCK);
        assertTrue(minimum > 0);
        assertFalse(shows(0, off));
        assertFalse(shows(0, immediate));
        assertFalse(shows(minimum - 1, off));
        assertFalse(shows(minimum - 1, immediate));
        assertTrue(shows(minimum, off));
        assertTrue(shows(minimum, immediate));
        assertTrue(shows(0, enforced));
        for (var deferred = 0; deferred < 60; deferred++) {
            assertEquals(ZoneCastOrder.Step.DEFER, severe(ZoneMode.OBSERVE, false, deferred, shows(0, off)));
            assertEquals(ZoneCastOrder.Step.DEFER, severe(ZoneMode.OBSERVE, false, deferred, shows(0, immediate)));
        }
        assertEquals(ZoneCastOrder.Step.SKIP, severe(ZoneMode.OBSERVE, false, 60, shows(0, off)));
        assertEquals(ZoneCastOrder.Step.OPEN, severe(ZoneMode.OBSERVE, false, 0, shows(0, enforced)));
        assertEquals(ZoneCastOrder.Step.OPEN, severe(ZoneMode.GUARD, false, 0, shows(minimum, off)));
    }

    @Test
    void entropyKeepsTheGuardLiftAndOtherwiseNeedsTheAuthorsWindUp() {
        var enforced = TelegraphSettings.DEFAULT;
        var minimum = enforced.minimumTicks(DangerClass.HEAVY, TellAnswer.DODGE);
        assertFalse(TelegraphPolicy.showsMinimum(true, true, DangerClass.HEAVY, TellAnswer.DODGE, 0, enforced));
        assertTrue(TelegraphPolicy.showsMinimum(true, true, DangerClass.HEAVY, TellAnswer.DODGE, minimum, enforced));
        assertEquals(ZoneCastOrder.Step.OPEN, ZoneCastOrder.step(true, ZoneMode.GUARD, true, DangerClass.HEAVY,
                Feasibility.FEASIBLE, DisplacementKind.STRONG_PUSH, PushDanger.LETHAL, 0, 60, 0, 40, false));
        assertEquals(ZoneCastOrder.Step.DEFER, ZoneCastOrder.step(true, ZoneMode.OBSERVE, true, DangerClass.HEAVY,
                Feasibility.FEASIBLE, DisplacementKind.STRONG_PUSH, PushDanger.LETHAL, 0, 60, 0, 40, false));
        assertEquals(ZoneCastOrder.Step.OPEN, ZoneCastOrder.step(true, ZoneMode.OBSERVE, true, DangerClass.HEAVY,
                Feasibility.FEASIBLE, DisplacementKind.STRONG_PUSH, PushDanger.LETHAL, 0, 60, 0, 40, true));
    }

    @Test
    void aCallerThatDoesNotKnowTheTellNeverPushesAPlayerIntoALethalDrop() {
        assertEquals(ZoneCastOrder.Step.DEFER, ZoneCastOrder.step(true, ZoneMode.OBSERVE, false, DangerClass.SEVERE,
                Feasibility.FEASIBLE, DisplacementKind.KNOCKBACK, PushDanger.LETHAL, 0, 60, 0, 40));
        assertEquals(PushDecision.DEFER, PushPolicy.decide(true, DisplacementKind.KNOCKBACK, PushDanger.LETHAL,
                DangerClass.HEAVY, Feasibility.FEASIBLE, 0, 60, false, ZoneMode.OBSERVE));
        assertEquals(PushDecision.SKIP, PushPolicy.decide(true, DisplacementKind.KNOCKBACK, PushDanger.LETHAL,
                DangerClass.HEAVY, Feasibility.FEASIBLE, 60, 60, false, ZoneMode.OBSERVE));
        assertEquals(PushDecision.ALLOW, PushPolicy.decide(false, DisplacementKind.KNOCKBACK, PushDanger.LETHAL,
                DangerClass.HEAVY, Feasibility.FEASIBLE, 0, 60, false, ZoneMode.OBSERVE));
        assertEquals(PushDecision.ALLOW, PushPolicy.decide(true, DisplacementKind.PULL, PushDanger.LETHAL,
                DangerClass.HEAVY, Feasibility.FEASIBLE, 0, 60, false, ZoneMode.OBSERVE));
        assertEquals(PushDecision.ALLOW, PushPolicy.decide(true, DisplacementKind.KNOCKBACK, PushDanger.HARM,
                DangerClass.HEAVY, Feasibility.FEASIBLE, 0, 60, false, ZoneMode.OBSERVE));
    }

    @Test
    void aHeavyTellWithAnInfeasibleOrUnknownAnswerStillDefers() {
        assertEquals(PushDecision.DEFER, PushPolicy.decide(true, DisplacementKind.KNOCKBACK, PushDanger.LETHAL,
                DangerClass.HEAVY, Feasibility.UNKNOWN, 0, 60, false, ZoneMode.OBSERVE, true));
        assertEquals(PushDecision.DEFER, PushPolicy.decide(true, DisplacementKind.KNOCKBACK, PushDanger.LETHAL,
                DangerClass.HEAVY, Feasibility.INFEASIBLE, 0, 60, false, ZoneMode.OBSERVE, true));
        assertEquals(PushDecision.ALLOW, PushPolicy.decide(true, DisplacementKind.KNOCKBACK, PushDanger.LETHAL,
                DangerClass.HEAVY, Feasibility.FEASIBLE, 0, 60, false, ZoneMode.OBSERVE, true));
    }

    private static boolean shows(int windUp, TelegraphSettings settings) {
        return TelegraphPolicy.showsMinimum(true, false, DangerClass.SEVERE, TellAnswer.BLOCK, windUp, settings);
    }

    private static ZoneCastOrder.Step severe(ZoneMode mode, boolean entropy, int deferred, boolean shows) {
        return ZoneCastOrder.step(true, mode, entropy, DangerClass.SEVERE, Feasibility.FEASIBLE,
                DisplacementKind.KNOCKBACK, PushDanger.LETHAL, deferred, 60, 0, 40, shows);
    }

    private static TelegraphSettings tells(boolean againstPlayers, ShortWindUp shortWindUp) {
        var base = TelegraphSettings.DEFAULT;
        return new TelegraphSettings(againstPlayers, base.againstEntities(), base.light(), base.medium(), base.heavy(),
                base.severe(), base.blockTicks(), base.dodgeTicks(), base.backOffTicks(), base.breakSightTicks(),
                base.interruptTicks(), base.deliveryMarginTicks(), base.maxReleasesPerTick(),
                base.whiffRecoveryTicks(), base.whiffCooldownTicks(), base.resultWindowTicks(),
                base.commitmentPercent(), base.entropyCommitmentPercent(), shortWindUp, base.defaultRecoveryTicks());
    }
}
