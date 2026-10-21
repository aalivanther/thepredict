package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Slack anchors after the other terms and fires at anchor + slack (M22.7, B1). */
class LeverApplicationTest {
    @Test
    void slackTwelveFiresAtReadySincePlusTwelveOnEvenAndOddRechecks() {
        var anchor = CastSlack.UNANCHORED;
        for (var tick = 0L; tick <= 20L; tick++) {
            anchor = CastSlack.anchor(anchor, tick, tick >= 4L);
            var ready = CastSlack.ready(4L, anchor, 12, true);
            if (tick < 16L) {
                assertTrue(ready > tick || tick < 4L, "tick " + tick + " ready " + ready);
            }
        }
        assertEquals(4L, anchor);
        assertEquals(16L, CastSlack.ready(4L, anchor, 12, true));
        assertEquals(16L, CastSlack.ready(4L, anchor, 12, true));
    }

    @Test
    void cooldownTenPlusSlackSixFiresAtSixteen() {
        var anchor = CastSlack.UNANCHORED;
        for (var tick = 0L; tick < 10L; tick++) {
            anchor = CastSlack.anchor(anchor, tick, false);
            assertEquals(CastSlack.UNANCHORED, anchor);
        }
        anchor = CastSlack.anchor(anchor, 10L, true);
        assertEquals(10L, anchor);
        assertEquals(16L, CastSlack.ready(10L, anchor, 6, true));
    }

    @Test
    void outOfReachClearsTheAnchorAndFiresSlackAfterTheReturn() {
        var anchor = CastSlack.anchor(CastSlack.UNANCHORED, 0L, true);
        for (var tick = 1L; tick <= 30L; tick++) {
            anchor = CastSlack.anchor(anchor, tick, false);
        }
        assertEquals(CastSlack.UNANCHORED, anchor);
        anchor = CastSlack.anchor(anchor, 31L, true);
        assertEquals(31L, anchor);
        assertEquals(43L, CastSlack.ready(31L, anchor, 12, true));
    }

    @Test
    void floorAppliesToPlayersAndNotToEntitiesAndLocalPickPenalisesOnlyHigh() {
        var player = PressureLadder.levers(8, 0, 5, true);
        var entity = PressureLadder.levers(8, 0, 5, false);
        assertEquals(5, player.reactionDelayTicks());
        assertEquals(0, entity.reactionDelayTicks());
        assertEquals(PressureLadder.bench(7), PressureLadder.levers(8, 7, 5, false));
        var high = new CandidateIntent(new TacticId("high"), Movement.APPROACH, Optional.empty(), 800, 0,
                DamageTier.HIGH);
        var plain = new CandidateIntent(new TacticId("plain"), Movement.HOLD, Optional.empty(), 700, 0);
        var set = new CandidateSet(0L, 1, List.of(high, plain), Map.of(), List.of(high.tactic(), plain.tactic()));
        assertEquals(0, set.localPick(new PressureLevers(0, ReactionMask.ALL, 0, true, 0)));
        assertEquals(1, set.localPick(new PressureLevers(0, ReactionMask.ALL, 0, true, 150)));
        assertTrue(CastSlack.damaging(SkillChoice.untyped(new ChoiceId("bolt"), "bolt", Set.of(), true, 0)));
        var escape = new SkillPurpose(SkillRole.ESCAPE, Set.of(SkillRole.ESCAPE), Delivery.SELF, OptionalDouble.empty(),
                OptionalDouble.empty(), 0, CooldownClass.SHORT, DamageTier.NONE, Set.of(), Set.of(), false,
                PurposeOrigin.DECLARED);
        assertFalse(CastSlack.damaging(SkillChoice.typed(new ChoiceId("out"), "out", Set.of(), false, 0, escape)));
    }
}
