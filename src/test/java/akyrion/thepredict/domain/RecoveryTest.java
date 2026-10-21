package akyrion.thepredict.domain;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Recovery blocks damaging casts and keeps only the three escape reactions (M23.6, decision 6 of the user). */
class RecoveryTest {
    private static final long TARGET = (1L << TickSignals.VALID) | (1L << TickSignals.HAS_TARGET);

    private final MovementSettings movement = MovementSettings.DEFAULTS;

    @Test
    void recoveryRefusesDamageAndAllowsEscapeOrDefense() {
        var damage = choice(SkillRole.DAMAGE, DamageTier.HIGH, Delivery.MELEE);
        var escape = choice(SkillRole.ESCAPE, DamageTier.NONE, Delivery.SELF);
        var defense = choice(SkillRole.DEFENSE, DamageTier.NONE, Delivery.SELF);
        assertFalse(ReadyCastOrder.stanceAllows(damage, false, ActionStance.RECOVERY, BaseStance.NEUTRAL,
                StanceSettings.DEFAULT));
        assertFalse(ReadyCastOrder.stanceAllows(damage, true, ActionStance.WINDUP, BaseStance.NEUTRAL,
                StanceSettings.DEFAULT));
        assertFalse(ReadyCastOrder.stanceAllows(escape, true, ActionStance.STAGGERED, BaseStance.NEUTRAL,
                StanceSettings.DEFAULT));
        assertTrue(ReadyCastOrder.stanceAllows(escape, false, ActionStance.RECOVERY, BaseStance.NEUTRAL,
                StanceSettings.DEFAULT));
        assertTrue(ReadyCastOrder.stanceAllows(defense, false, ActionStance.RECOVERY, BaseStance.GUARDED,
                StanceSettings.DEFAULT));
    }

    @Test
    void onlyANonDamagingEscapeOrDefenseMayStartBeforeTheBusyDeadlineOfARecovery() {
        var escape = choice(SkillRole.ESCAPE, DamageTier.NONE, Delivery.SELF);
        var defense = choice(SkillRole.DEFENSE, DamageTier.NONE, Delivery.SELF);
        var damage = choice(SkillRole.DAMAGE, DamageTier.HIGH, Delivery.MELEE);
        var damagingEscape = choice(SkillRole.ESCAPE, DamageTier.LOW, Delivery.SELF);
        var untyped = SkillChoice.untyped(new ChoiceId("raw"), "Raw", Set.of(), false, 0);
        assertTrue(ReadyCastOrder.startsWhileBusy(escape, ActionStance.RECOVERY));
        assertTrue(ReadyCastOrder.startsWhileBusy(defense, ActionStance.RECOVERY));
        assertFalse(ReadyCastOrder.startsWhileBusy(damage, ActionStance.RECOVERY));
        assertFalse(ReadyCastOrder.startsWhileBusy(damagingEscape, ActionStance.RECOVERY));
        assertFalse(ReadyCastOrder.startsWhileBusy(untyped, ActionStance.RECOVERY));
        for (var other : new ActionStance[] {ActionStance.NONE, ActionStance.WINDUP, ActionStance.STAGGERED}) {
            assertFalse(ReadyCastOrder.startsWhileBusy(escape, other), other.name());
            assertFalse(ReadyCastOrder.startsWhileBusy(defense, other), other.name());
        }
        for (var action : ActionStance.values()) {
            for (var candidate : new SkillChoice[] {escape, defense, damage, damagingEscape, untyped}) {
                if (ReadyCastOrder.startsWhileBusy(candidate, action)) {
                    assertTrue(ReadyCastOrder.stanceAllows(candidate, true, action, BaseStance.NEUTRAL,
                            StanceSettings.DEFAULT));
                }
            }
        }
    }

    @Test
    void recoveryKeepsOnlyEvadeDodgeAndSpaceOut() {
        var cooldown = new long[Reaction.values().length];
        var since = new long[cooldown.length];
        var critical = TickSignals.pack(TARGET, 0, 0, 0, 0, 10, 80);
        assertEquals(Reaction.EVADE_CRITICAL.ordinal(), ReactionPolicy.select(ActionStance.RECOVERY, critical, 5, 0,
                ReactionScope.ALL, ReactionMask.ALL, false, true, Movement.APPROACH, 0.3, movement, true, 0,
                cooldown, since));
        assertEquals(ReactionPolicy.NONE, ReactionPolicy.select(ActionStance.WINDUP, critical, 5, 0,
                ReactionScope.ALL, ReactionMask.ALL, false, true, Movement.APPROACH, 0.3, movement, true, 0,
                cooldown, since));
        assertEquals(ReactionPolicy.NONE, ReactionPolicy.select(ActionStance.STAGGERED, critical, 5, 0,
                ReactionScope.ALL, ReactionMask.ALL, false, true, Movement.APPROACH, 0.3, movement, true, 0,
                cooldown, since));
        var consuming = TickSignals.pack(TARGET | (1L << TickSignals.TARGET_CONSUMING), 40, 0, 0, 0, 80, 80);
        assertEquals(Reaction.PUNISH_CONSUMING.ordinal(), ReactionPolicy.select(consuming, 5, 0, ReactionScope.ALL,
                ReactionMask.ALL, false, true, Movement.APPROACH, 0.3, movement, true, 0, cooldown, since));
        assertEquals(ReactionPolicy.NONE, ReactionPolicy.select(ActionStance.RECOVERY, consuming, 5, 0,
                ReactionScope.ALL, ReactionMask.ALL, false, true, Movement.APPROACH, 0.3, movement, true, 0,
                cooldown, since));
        var shot = TickSignals.pack(TARGET | (1L << TickSignals.INCOMING_PROJECTILE), 40, 0, 0, 0, 80, 80);
        assertEquals(Reaction.DODGE_SHOT.ordinal(), ReactionPolicy.select(ActionStance.RECOVERY, shot, 5, 0,
                ReactionScope.ALL, ReactionMask.ALL, true, true, Movement.KEEP_DISTANCE, 0.3, movement, true, 0,
                cooldown, since));
    }

    @Test
    void aForbiddenReactionAheadInTheTableDoesNotHideSpaceOutDuringRecovery() {
        var cooldown = new long[Reaction.values().length];
        var since = new long[cooldown.length];
        var lowTarget = TickSignals.pack(TARGET, 8, 0, 0, 0, 80, 10);
        var busyEnded = 4L;
        var now = 5L;
        assertTrue(ReactionPolicy.trigger(Reaction.FINISH, lowTarget, Movement.KEEP_DISTANCE, 0.3, movement, true,
                busyEnded, now));
        assertTrue(ReactionPolicy.trigger(Reaction.SPACE_OUT, lowTarget, Movement.KEEP_DISTANCE, 0.3, movement, true,
                busyEnded, now));
        assertEquals(Reaction.FINISH.ordinal(), ReactionPolicy.select(ActionStance.NONE, lowTarget, now, 0,
                ReactionScope.ALL, ReactionMask.ALL, true, true, Movement.KEEP_DISTANCE, 0.3, movement, true,
                busyEnded, cooldown, since));
        assertEquals(Reaction.SPACE_OUT.ordinal(), ReactionPolicy.select(ActionStance.RECOVERY, lowTarget, now, 0,
                ReactionScope.ALL, ReactionMask.ALL, true, true, Movement.KEEP_DISTANCE, 0.3, movement, true,
                busyEnded, cooldown, since));
        assertEquals(Reaction.SPACE_OUT.ordinal(), ReactionPolicy.select(ActionStance.RECOVERY, lowTarget, now, 0,
                ReactionScope.ALL, ReactionMask.ALL, true, true, Movement.KEEP_DISTANCE, 0.3, movement, true,
                busyEnded, cooldown, since, false, false, false));
        for (var reaction : Reaction.values()) {
            assertEquals(reaction == Reaction.EVADE_CRITICAL || reaction == Reaction.DODGE_SHOT
                    || reaction == Reaction.SPACE_OUT, ReactionPolicy.allowedIn(ActionStance.RECOVERY, reaction));
            assertFalse(ReactionPolicy.allowedIn(ActionStance.WINDUP, reaction));
            assertFalse(ReactionPolicy.allowedIn(ActionStance.STAGGERED, reaction));
            assertTrue(ReactionPolicy.allowedIn(ActionStance.NONE, reaction));
        }
    }

    private static SkillChoice choice(SkillRole role, DamageTier damage, Delivery delivery) {
        var reach = delivery == Delivery.MELEE ? OptionalDouble.of(3) : OptionalDouble.empty();
        var purpose = new SkillPurpose(role, Set.of(role), delivery, reach, OptionalDouble.empty(), 0,
                CooldownClass.SHORT, damage, Set.of(), Set.of(), false, PurposeOrigin.DECLARED);
        return SkillChoice.typed(new ChoiceId(role.name().toLowerCase() + "_" + damage.name().toLowerCase()),
                role.name(), Set.of(), false, 0, purpose);
    }
}
