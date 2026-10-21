package akyrion.thepredict.engine;

import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.ReleaseOutcome;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TacticId;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ten thousand combats of five members. Commitment 100 never feints. Each tell is opened and released through
 * {@link TelegraphRelease}. The release does not ask the books again.
 */
class NoFeintGroupTest {
    @Test
    void tenThousandCombatsOfFiveOpenAndReleaseThroughTheLane() {
        assertEquals(100, akyrion.thepredict.domain.TelegraphSettings.DEFAULT.commitmentPercent());
        var choice = severeMelee();
        var sessions = new MobSession[5];
        for (var who = 0; who < sessions.length; who++) {
            sessions[who] = session(choice);
            sessions[who].playerTarget(true);
            sessions[who].recordTarget(UUID.randomUUID(), 3);
            sessions[who].groupCopy().sight(1);
        }
        var released = 0;
        var spacing = Math.max(GroupCaps.DEFAULT.minImpactGapTicks(), GroupCaps.DEFAULT.bigImpactSpacingTicks()) + 2;
        for (var combat = 0; combat < 10_000; combat++) {
            var attack = new AttackLedger();
            var tells = new TellLedger();
            for (var who = 0; who < sessions.length; who++) {
                var caster = sessions[who];
                caster.reseed(combat);
                caster.ledgerWaitUntilTick(0);
                var tick = (long) who * spacing;
                caster.groupBooks(books(attack, tells, who));
                assertTrue(GroupCastGate.admit(caster, choice, tick, true).isEmpty());
                assertTrue(TellOpening.hold(caster, choice, Movement.NONE, tick, QuietActions.INSTANCE, new long[5]));
                assertFalse(caster.tellFeint());
                var before = attack.size(0);
                var calls = new int[1];
                var outcome = TelegraphRelease.release(caster, caster.releaseTick(), releasing(attack, before, calls),
                        new long[5]);
                assertEquals(ReleaseOutcome.RELEASED, outcome);
                assertEquals(1, calls[0]);
                assertEquals(before, attack.size(0));
                assertFalse(attack.holding(0, who, akyrion.thepredict.domain.AttackPool.MELEE));
                released++;
            }
        }
        assertEquals(50_000, released);
    }

    private static CombatActions releasing(AttackLedger attack, int before, int[] calls) {
        return new CombatActions() {
            @Override
            public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
                return ExecutionOutcome.NOT_ELIGIBLE;
            }

            @Override
            public boolean ready(MobKey mob, SkillChoice choice) {
                return false;
            }

            @Override
            public void signalTactic(MobKey mob, TacticId tactic) {
            }

            @Override
            public void addThreat(MobKey mob, UUID participantId, double amount) {
            }

            @Override
            public ReleaseOutcome release(MobKey mob, SkillChoice choice, Movement movement, UUID expectedTarget,
                                          akyrion.thepredict.engine.CastInterrupt interrupt) {
                assertEquals(before, attack.size(0));
                calls[0]++;
                return ReleaseOutcome.RELEASED;
            }
        };
    }

    private static GroupBooks books(AttackLedger attack, TellLedger tells, int who) {
        return new GroupBooks(attack, tells, GroupCaps.DEFAULT, GroupMode.FAIR, who, 0, BenchPolicy.FAIR, false,
                false, 4, GroupCaps.DEFAULT.minImpactGapTicks(), false);
    }

    private static SkillChoice severeMelee() {
        var id = new ChoiceId("slam");
        var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(3),
                OptionalDouble.empty(), 0, CooldownClass.SHORT, DamageTier.HIGH, Set.of(), Set.of(), true,
                PurposeOrigin.DECLARED);
        return SkillChoice.typed(id, "Slam", Set.of(), true, 0, purpose);
    }

    private static MobSession session(SkillChoice choice) {
        var base = EngineFixtures.profile("Guardian");
        var profile = new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(),
                Map.of(choice.id(), choice), base.contexts(), base.movement(), base.health(), base.threat(),
                base.baseAi(), base.timing());
        return new MobSession(new MobKey(UUID.randomUUID(), 0), profile);
    }
}
