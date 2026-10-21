package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Direction table and candidate order of a ready cast (M19.5). */
class ReadyCastOrderTest {
    private static final MovementSettings SETTINGS = MovementSettings.DEFAULTS;

    @Test
    void gapCloseFiresOnApproachFlankAndKeepDistanceOnlyBeyondTheOuterEdge() {
        var gap = purpose(SkillRole.GAP_CLOSE);
        assertTrue(allows(gap, Movement.APPROACH, 0));
        assertTrue(allows(gap, Movement.FLANK, 4));
        assertTrue(allows(gap, Movement.KEEP_DISTANCE, 9.0001));
        assertFalse(allows(gap, Movement.KEEP_DISTANCE, 9));
        assertFalse(allows(gap, Movement.KEEP_DISTANCE, 7));
        assertFalse(allows(gap, Movement.RETREAT, 20));
        assertFalse(allows(gap, Movement.HOLD, 20));
        assertFalse(allows(gap, Movement.NONE, 20));
    }

    @Test
    void escapeFiresOnRetreatAndKeepDistanceOnlyInsideTheInnerEdge() {
        var escape = purpose(SkillRole.ESCAPE);
        assertTrue(allows(escape, Movement.RETREAT, 12));
        assertTrue(allows(escape, Movement.KEEP_DISTANCE, 4.9999));
        assertFalse(allows(escape, Movement.KEEP_DISTANCE, 5));
        assertFalse(allows(escape, Movement.KEEP_DISTANCE, 7));
        assertFalse(allows(escape, Movement.APPROACH, 0));
        assertFalse(allows(escape, Movement.FLANK, 0));
        assertFalse(allows(escape, Movement.HOLD, 0));
        assertFalse(allows(escape, Movement.NONE, 0));
    }

    @Test
    void evadeCriticalLetsEscapeFireOnHoldNoneAndRetreatButNotApproach() {
        var escape = purpose(SkillRole.ESCAPE);
        assertTrue(ReadyCastOrder.directionAllows(escape, Movement.HOLD, 0, SETTINGS, true, false));
        assertTrue(ReadyCastOrder.directionAllows(escape, Movement.NONE, 0, SETTINGS, true, false));
        assertTrue(ReadyCastOrder.directionAllows(escape, Movement.RETREAT, 0, SETTINGS, true, false));
        assertFalse(ReadyCastOrder.directionAllows(escape, Movement.APPROACH, 0, SETTINGS, true, false));
        assertFalse(ReadyCastOrder.directionAllows(escape, Movement.HOLD, 0, SETTINGS, false, false));
        assertFalse(ReadyCastOrder.directionAllows(escape, Movement.NONE, 0, SETTINGS, false, false));
    }

    @Test
    void punishConsumingLetsGapCloseFireOnHoldNoneAndApproachButNotRetreat() {
        var gap = purpose(SkillRole.GAP_CLOSE);
        assertTrue(ReadyCastOrder.directionAllows(gap, Movement.HOLD, 0, SETTINGS, false, true));
        assertTrue(ReadyCastOrder.directionAllows(gap, Movement.NONE, 0, SETTINGS, false, true));
        assertTrue(ReadyCastOrder.directionAllows(gap, Movement.APPROACH, 0, SETTINGS, false, true));
        assertFalse(ReadyCastOrder.directionAllows(gap, Movement.RETREAT, 0, SETTINGS, false, true));
        assertFalse(ReadyCastOrder.directionAllows(gap, Movement.HOLD, 0, SETTINGS, false, false));
        assertFalse(ReadyCastOrder.directionAllows(gap, Movement.NONE, 0, SETTINGS, false, false));
    }

    @Test
    void bothRolesNeverFireAndNeitherRoleAlwaysFires() {
        var both = purpose(SkillRole.GAP_CLOSE, SkillRole.ESCAPE);
        var plain = purpose(SkillRole.DAMAGE);
        for (var movement : Movement.values()) {
            for (var distance : new double[] {0, 4, 5, 7, 9, 9.0001, 16}) {
                assertFalse(allows(both, movement, distance), movement + " @ " + distance);
                assertTrue(allows(plain, movement, distance), movement + " @ " + distance);
            }
        }
    }

    @Test
    void moreBestWhenThenHigherDamageThenDeclarationOrderAndUnratedFollowsLow() {
        assertTrue(ReadyCastOrder.compare(2, DamageTier.LOW, 5, 1, DamageTier.HIGH, 0) < 0);
        assertTrue(ReadyCastOrder.compare(1, DamageTier.HIGH, 5, 1, DamageTier.MEDIUM, 0) < 0);
        assertTrue(ReadyCastOrder.compare(1, DamageTier.MEDIUM, 5, 1, DamageTier.LOW, 0) < 0);
        assertTrue(ReadyCastOrder.compare(1, DamageTier.LOW, 5, 1, DamageTier.UNRATED, 0) < 0);
        assertTrue(ReadyCastOrder.compare(1, DamageTier.UNRATED, 5, 1, DamageTier.NONE, 0) < 0);
        assertTrue(ReadyCastOrder.compare(0, DamageTier.LOW, 1, 0, DamageTier.LOW, 2) < 0);
        assertEquals(0, ReadyCastOrder.compare(1, DamageTier.MEDIUM, 3, 1, DamageTier.MEDIUM, 3));
        assertTrue(ReadyCastOrder.rank(DamageTier.UNRATED) < ReadyCastOrder.rank(DamageTier.LOW));
        assertTrue(ReadyCastOrder.rank(DamageTier.HIGH) > ReadyCastOrder.rank(DamageTier.NONE));
    }

    @Test
    void bestWhenNowCountsTheIntersection() {
        assertEquals(2, ReadyCastOrder.bestWhenNow(
                Set.of(UseCondition.TARGET_IN_MELEE, UseCondition.TARGET_FLEEING, UseCondition.ENEMIES_GROUPED),
                Set.of(UseCondition.TARGET_IN_MELEE, UseCondition.SELF_LOW_HEALTH, UseCondition.TARGET_FLEEING)));
        assertEquals(0, ReadyCastOrder.bestWhenNow(Set.of(UseCondition.TARGET_SHIELDING), Set.of()));
    }

    @Test
    void aDamagingGapCloseInMeleeFiresWithAnyMovementButRetreatAndAnArmedEscape() {
        var leap = purpose(SkillRole.GAP_CLOSE, SkillRole.DAMAGE);
        for (var movement : List.of(Movement.HOLD, Movement.NONE, Movement.KEEP_DISTANCE, Movement.APPROACH)) {
            assertTrue(ReadyCastOrder.directionAllows(leap, movement, 2, SETTINGS, false, false, true, true),
                    movement.name());
        }
        assertFalse(ReadyCastOrder.directionAllows(leap, Movement.RETREAT, 2, SETTINGS, false, false, true, true));
        assertFalse(ReadyCastOrder.directionAllows(leap, Movement.HOLD, 2, SETTINGS, true, false, true, true),
                "an armed escape keeps the leap back");
        assertFalse(ReadyCastOrder.directionAllows(leap, Movement.HOLD, 6, SETTINGS, false, false, true, false),
                "away from melee the old rows stand");
        assertFalse(ReadyCastOrder.directionAllows(leap, Movement.KEEP_DISTANCE, 6, SETTINGS, false, false, true,
                false), "a band that holds is worth more than the leap");
        assertFalse(ReadyCastOrder.directionAllows(leap, Movement.HOLD, 2, SETTINGS, false, false, false, true),
                "out of its reach");
        assertFalse(ReadyCastOrder.directionAllows(purpose(SkillRole.GAP_CLOSE), Movement.HOLD, 2, SETTINGS, false,
                false, true, true), "a gap-close without damage gains nothing in melee");
        assertFalse(ReadyCastOrder.directionAllows(leap, Movement.HOLD, 2, SETTINGS, false, false),
                "the overload without the reach keeps the old rows");
    }

    @Test
    void sustainAndDefenseGoFirstOnlyWhileTheirBestWhenHolds() {
        var heal = withBestWhen(purpose(SkillRole.SUSTAIN), UseCondition.SELF_LOW_HEALTH);
        var guard = withBestWhen(purpose(SkillRole.DEFENSE), UseCondition.TARGET_IN_MELEE);
        var strike = withBestWhen(purpose(SkillRole.DAMAGE), UseCondition.TARGET_IN_MELEE);
        assertTrue(ReadyCastOrder.keepsAlive(heal, Set.of(UseCondition.SELF_LOW_HEALTH)));
        assertFalse(ReadyCastOrder.keepsAlive(heal, Set.of(UseCondition.TARGET_IN_MELEE)));
        assertTrue(ReadyCastOrder.keepsAlive(guard, Set.of(UseCondition.TARGET_IN_MELEE)));
        assertFalse(ReadyCastOrder.keepsAlive(strike, Set.of(UseCondition.TARGET_IN_MELEE)), "damage is not sustain");
        assertFalse(ReadyCastOrder.keepsAlive(purpose(SkillRole.SUSTAIN), Set.of(UseCondition.SELF_LOW_HEALTH)),
                "without a declared BestWhen it keeps its place");
    }

    @Test
    void anyReadyIsTheOnlyDefault() {
        assertEquals(CastMode.ANY_READY, CastMode.DEFAULT);
    }

    private static boolean allows(SkillPurpose purpose, Movement movement, double distanceBlocks) {
        return ReadyCastOrder.directionAllows(purpose, movement, distanceBlocks, SETTINGS);
    }

    private static SkillPurpose withBestWhen(SkillPurpose purpose, UseCondition condition) {
        return new SkillPurpose(purpose.primary(), purpose.roles(), purpose.delivery(), purpose.reachBlocks(),
                purpose.radiusBlocks(), purpose.windUpTicks(), purpose.cooldown(), purpose.damage(), Set.of(condition),
                purpose.risks(), purpose.ignoresShield(), purpose.origin());
    }

    private static SkillPurpose purpose(SkillRole... roles) {
        var damaging = Set.of(roles).contains(SkillRole.DAMAGE) || Set.of(roles).contains(SkillRole.FINISHER);
        return new SkillPurpose(roles[0], Set.of(roles), Delivery.SELF, OptionalDouble.empty(), OptionalDouble.empty(),
                0, CooldownClass.UNKNOWN, damaging ? DamageTier.UNRATED : DamageTier.NONE, Set.of(), Set.of(), false,
                PurposeOrigin.DECLARED);
    }
}
