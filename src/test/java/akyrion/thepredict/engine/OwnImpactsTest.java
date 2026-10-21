package akyrion.thepredict.engine;

import akyrion.thepredict.AllocationProbe;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ring of own hits in flight (fase de efectividad E-1): the vanilla window of invulnerability with the rule of the
 * excess, the cap of {@value OwnImpacts#SLOTS} hits (A5), the reset on a new target and no allocation (C12). Weights:
 * 1 low, 2 medium, 3 high.
 */
class OwnImpactsTest {
    private static final UUID TARGET = new UUID(1L, 2L);
    private static final UUID OTHER = new UUID(3L, 4L);

    private final OwnImpacts ring = new OwnImpacts();

    @Test
    void anEmptyRingNeverDelays() {
        assertEquals(25, ring.freeLanding(TARGET, 25, 2, 15));
        ring.retarget(TARGET);
        assertEquals(25, ring.freeLanding(TARGET, 25, 2, 15));
    }

    @Test
    void aWeakerOrEqualHitInsideTheWindowWaitsForItsEnd() {
        ring.note(TARGET, 20, 2, 15);
        assertEquals(30, ring.freeLanding(TARGET, 25, 1, 16), "a weaker hit would deal nothing");
        assertEquals(30, ring.freeLanding(TARGET, 25, 2, 16), "an equal hit would deal nothing");
        assertEquals(30, ring.freeLanding(TARGET, 20, 2, 16), "the same tick is inside the window");
        assertEquals(30, ring.freeLanding(TARGET, 30, 1, 16), "the window ends after ten ticks");
    }

    @Test
    void aStrongerHitInsideTheWindowAddsOnlyTheExcessSoItWaitsForTheEndToo() {
        ring.note(TARGET, 20, 1, 10);
        assertEquals(30, ring.freeLanding(TARGET, 24, 3, 15),
                "vanilla: the high hit would add only what exceeds the low one");
    }

    @Test
    void aHitThatWouldOpenItsWindowOverAHitInFlightWaitsUntilAfterThatHit() {
        ring.note(TARGET, 30, 1, 20);
        assertEquals(40, ring.freeLanding(TARGET, 26, 2, 21), "the bolt in flight would land inside the new window");
        assertEquals(20, ring.freeLanding(TARGET, 20, 2, 10), "ten ticks before it, the two do not touch");
    }

    @Test
    void aStrongerHitInsideAWindowDoesNotReopenIt() {
        ring.note(TARGET, 20, 1, 10);
        ring.note(TARGET, 25, 3, 12);
        assertEquals(30, ring.freeLanding(TARGET, 26, 1, 15),
                "the window of the first hit ends at 30; the stronger hit inside it did not open its own");
    }

    @Test
    void theRingKeepsFourHitsAndForgetsTheEarliest() {
        for (var hit = 0; hit < 6; hit++) {
            ring.note(TARGET, 100 + 20L * hit, 1, 90);
        }
        assertEquals(OwnImpacts.SLOTS, ring.size());
        assertEquals(105, ring.freeLanding(TARGET, 105, 1, 90), "the hits at 100 and 120 were forgotten");
        assertEquals(150, ring.freeLanding(TARGET, 145, 1, 90), "the hit at 140 is still kept");
    }

    @Test
    void anotherTargetNeverWaitsAndEmptiesTheRing() {
        ring.note(TARGET, 20, 2, 10);
        assertEquals(22, ring.freeLanding(OTHER, 22, 2, 15));
        ring.retarget(OTHER);
        assertEquals(0, ring.size());
        assertEquals(22, ring.freeLanding(TARGET, 22, 2, 15));
    }

    @Test
    void aHitWhoseWindowPassedIsForgotten() {
        ring.note(TARGET, 20, 2, 10);
        assertEquals(1, ring.size());
        assertEquals(31, ring.freeLanding(TARGET, 31, 2, 30));
        assertEquals(0, ring.size());
    }

    @Test
    void theRingAllocatesNothing() {
        ring.retarget(TARGET);
        var tick = new long[] {1_000};
        var bytes = AllocationProbe.bytesAllocatedBy(() -> {
            ring.note(TARGET, tick[0] + 10, 1, tick[0]);
            ring.freeLanding(TARGET, tick[0] + 12, 2, tick[0]);
            ring.mayWait(0, tick[0], StrongerFirst.MAX_WAIT_TICKS);
            ring.waiting(0, tick[0], (tick[0] & 1L) == 0L);
            tick[0] += 3;
        });
        assertTrue(bytes < AllocationProbe.TOLERANCE_BYTES, bytes + " bytes");
    }

    @Test
    void aWaitForAStrongerHitEndsAfterItsCap() {
        assertTrue(ring.mayWait(2, 100, 20));
        ring.waiting(2, 100, true);
        assertTrue(ring.mayWait(2, 119, 20));
        assertFalse(ring.mayWait(2, 120, 20), "twenty ticks in a row is the cap");
        assertTrue(ring.mayWait(2, 121, 20), "the run starts again");
        assertFalse(ring.mayWait(-1, 100, 20));
    }

    @Test
    void hitsAndWeightsFollowThePurpose() {
        assertTrue(OwnImpacts.hits(typed(SkillRole.DAMAGE, DamageTier.HIGH, true)));
        assertFalse(OwnImpacts.hits(typed(SkillRole.DEFENSE, DamageTier.NONE, false)), "a guard on itself");
        assertTrue(OwnImpacts.hits(new SkillChoice(new ChoiceId("swing"), ChoiceKind.VANILLA_MELEE, Optional.empty(),
                Set.of(), true, 0)));
        assertTrue(OwnImpacts.hits(SkillChoice.untyped(new ChoiceId("bare"), "TPV_Bare", Set.of(), true, 0)));
        assertEquals(1, OwnImpacts.weight(typed(SkillRole.DAMAGE, DamageTier.LOW, true)));
        assertEquals(2, OwnImpacts.weight(typed(SkillRole.DAMAGE, DamageTier.MEDIUM, true)));
        assertEquals(3, OwnImpacts.weight(typed(SkillRole.DAMAGE, DamageTier.HIGH, true)));
        assertEquals(OwnImpacts.UNKNOWN_WEIGHT, OwnImpacts.weight(SkillChoice.untyped(new ChoiceId("bare"),
                "TPV_Bare", Set.of(), true, 0)));
        assertEquals(0, OwnImpacts.weight(typed(SkillRole.DEFENSE, DamageTier.NONE, false)));
    }

    private static SkillChoice typed(SkillRole role, DamageTier damage, boolean target) {
        var purpose = new SkillPurpose(role, Set.of(role), role == SkillRole.DEFENSE ? Delivery.SELF : Delivery.MELEE,
                role == SkillRole.DEFENSE ? OptionalDouble.empty() : OptionalDouble.of(2.5), OptionalDouble.empty(), 5,
                CooldownClass.UNKNOWN, damage, Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
        return SkillChoice.typed(new ChoiceId(role.name().toLowerCase(java.util.Locale.ROOT)), "TPV_" + role.name(),
                Set.of(), target, 0, purpose);
    }
}
