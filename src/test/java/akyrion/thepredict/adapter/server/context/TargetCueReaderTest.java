package akyrion.thepredict.adapter.server.context;

import akyrion.thepredict.domain.TargetCue;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetCueReaderTest {
    /** A calm, fully recharged player with nothing in use. */
    static ParticipantReading calm() {
        return new ParticipantReading(20, 0, 20, 0, 0, false, false, false, 1.0f, false, 0, false, false, false, 0,
                false, false, 0f, false, false);
    }

    private static ParticipantReading shield(boolean blocking, boolean active) {
        return new ParticipantReading(20, 0, 20, 0, 0, blocking, active, false, 1.0f, false, 0, false, false, false, 0,
                false, false, 0f, false, false);
    }

    private static ParticipantReading bow(int usedTicks) {
        return new ParticipantReading(20, 0, 20, 0, 0, false, false, false, 1.0f, true, usedTicks, false, false, false,
                0, false, false, 0f, false, false);
    }

    private static ParticipantReading attack(float cooldown) {
        return new ParticipantReading(20, 0, 20, 0, 0, false, false, false, cooldown, false, 0, false, false, false, 0,
                false, false, 0f, false, false);
    }

    private static ParticipantReading mace(boolean inHand, float fall) {
        return new ParticipantReading(20, 0, 20, 0, 0, false, false, false, 1.0f, false, 0, false, false, false, 0,
                false, false, fall, inHand, false);
    }

    private static ParticipantReading everything(boolean invisible) {
        return new ParticipantReading(20, 0, 20, 0, 0, true, true, true, 0.1f, true, 40, true, true, true, 30, true,
                true, 3f, true, invisible);
    }

    @Test
    void aCalmVisiblePlayerShowsNoCue() {
        assertEquals(Set.of(), TargetCueReader.cues(calm(), true, false));
    }

    @Test
    void raisingAndBlockingNeverAppearTogether() {
        for (var blocking : new boolean[] {false, true}) {
            for (var active : new boolean[] {false, true}) {
                var cues = TargetCueReader.cues(shield(blocking, active), true, false);
                assertFalse(cues.contains(TargetCue.BLOCKING) && cues.contains(TargetCue.RAISING_SHIELD),
                        blocking + " " + active);
            }
        }
        assertEquals(Set.of(TargetCue.RAISING_SHIELD), TargetCueReader.cues(shield(false, true), true, false));
        assertEquals(Set.of(TargetCue.BLOCKING), TargetCueReader.cues(shield(true, true), true, false));
    }

    @Test
    void anAttackRechargesBelowNinetyPercent() {
        assertTrue(TargetCueReader.attackRecharging(attack(Math.nextDown(0.9f))));
        assertFalse(TargetCueReader.attackRecharging(attack(0.9f)));
        assertFalse(TargetCueReader.attackRecharging(attack(1.0f)));
    }

    @Test
    void aBowIsFullyDrawnFromTwentyTicks() {
        assertEquals(Set.of(TargetCue.DRAWING_BOW), TargetCueReader.cues(bow(19), true, false));
        assertEquals(Set.of(TargetCue.DRAWING_BOW, TargetCue.BOW_FULLY_DRAWN), TargetCueReader.cues(bow(20), true, false));
    }

    @Test
    void aMaceDiveNeedsTheMaceAndMoreThanOneAndAHalfBlocksOfFall() {
        assertFalse(TargetCueReader.maceDive(mace(true, 1.5f)));
        assertTrue(TargetCueReader.maceDive(mace(true, Math.nextUp(1.5f))));
        assertFalse(TargetCueReader.maceDive(mace(false, 10f)));
    }

    @Test
    void aShieldIsDisabledByItsCooldownOrByAFreshEvent() {
        assertTrue(TargetCueReader.shieldDisabled(everything(false), false));
        assertTrue(TargetCueReader.shieldDisabled(calm(), true));
        assertFalse(TargetCueReader.shieldDisabled(calm(), false));
        assertEquals(Set.of(TargetCue.SHIELD_DISABLED), TargetCueReader.cues(calm(), true, true));
    }

    @Test
    void everyItemCueIsReadWhenSeen() {
        var expected = EnumSet.allOf(TargetCue.class);
        expected.remove(TargetCue.INVISIBLE);
        expected.remove(TargetCue.RAISING_SHIELD);
        assertEquals(expected, TargetCueReader.cues(everything(false), true, false));
    }

    @Test
    void anInvisiblePlayerShowsOnlyInvisibleWithOrWithoutSight() {
        assertEquals(Set.of(TargetCue.INVISIBLE), TargetCueReader.cues(everything(true), true, true));
        assertEquals(Set.of(TargetCue.INVISIBLE), TargetCueReader.cues(everything(true), false, false));
    }

    @Test
    void noVisualCueAppearsWithoutLineOfSight() {
        assertEquals(Set.of(), TargetCueReader.cues(everything(false), false, true));
    }

    @Test
    void pearlCooldownNeedsOneTickLeft() {
        var cooling = new ParticipantReading(20, 0, 20, 0, 0, false, false, false, 1.0f, false, 0, false, false, false,
                1, false, false, 0f, false, false);
        assertTrue(TargetCueReader.pearlOnCooldown(cooling));
        assertFalse(TargetCueReader.pearlOnCooldown(calm()));
    }
}
