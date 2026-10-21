package akyrion.thepredict.engine;

import akyrion.thepredict.domain.BenchPolicy;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceTelegraph;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.GroupCaps;
import akyrion.thepredict.domain.GroupFairnessReason;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TelegraphSettings;
import akyrion.thepredict.domain.TellAnswer;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The group gate reserves the impact of a tell where it really lands: at the end of the shown length (PZ-05, A13). */
class GroupCastGateTest {
    private static final TelegraphSettings TELLS = TelegraphSettings.DEFAULT;

    @Test
    void twoTellsOfDifferentMembersWhoseRealImpactsMeetAreSpacedByTheShownLength() {
        var attack = new AttackLedger();
        var tells = new TellLedger();
        var first = caster(1, attack, tells, 0);
        var second = caster(2, attack, tells, 1);
        var heavy = told(DangerClass.HEAVY);
        var medium = told(DangerClass.MEDIUM);
        var heavyShown = TELLS.minimumTicks(DangerClass.HEAVY, TellAnswer.DODGE);
        var mediumShown = TELLS.minimumTicks(DangerClass.MEDIUM, TellAnswer.DODGE);
        var spacing = GroupCaps.DEFAULT.bigImpactSpacingTicks();
        var gap = GroupCaps.DEFAULT.minImpactGapTicks();
        var opensAt = heavyShown - mediumShown + gap;
        assertTrue(opensAt >= spacing, "the casts are a full spacing apart, so the wind-ups alone never meet");
        assertTrue(gap < spacing, "the real impacts are past the attack gap and inside the big spacing");

        assertTrue(GroupCastGate.admit(first, heavy, 0, true).isEmpty());
        assertEquals(heavyShown, first.groupTellImpact(), "a lead of the whole minimum lands when the tell ends");
        assertEquals(GroupFairnessReason.BIG_IMPACT_SPACING,
                GroupCastGate.admit(second, medium, opensAt, true).orElseThrow());
    }

    @Test
    void aCastWithoutATellStillReservesItsWindUp() {
        var attack = new AttackLedger();
        var first = caster(1, attack, new TellLedger(), 0);
        assertTrue(GroupCastGate.admit(first, told(DangerClass.MEDIUM), 5, false).isEmpty());
        assertEquals(5L, first.groupTellImpact(), "an untold cast of wind-up 0 lands where it starts");
    }

    private static MobSession caster(int id, AttackLedger attack, TellLedger tells, int seat) {
        var session = new MobSession(new MobKey(EngineFixtures.id(id), 0), EngineFixtures.profile("Guardian"));
        session.playerTarget(true);
        session.recordTarget(EngineFixtures.player(1), 3);
        session.groupCopy().sight(1);
        session.groupBooks(new GroupBooks(attack, tells, GroupCaps.DEFAULT, GroupMode.FAIR, seat, 0, BenchPolicy.FAIR,
                false, false, GroupCaps.DEFAULT.maxMeleeAttackersPerPlayer(), GroupCaps.DEFAULT.minImpactGapTicks(),
                false));
        return session;
    }

    private static SkillChoice told(DangerClass danger) {
        var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(3),
                OptionalDouble.empty(), 0, CooldownClass.SHORT, DamageTier.MEDIUM, Set.of(), Set.of(), true,
                PurposeOrigin.DECLARED);
        var name = danger.name().toLowerCase(Locale.ROOT);
        return SkillChoice.typed(new ChoiceId(name), name, Set.of(), true, 0, purpose)
                .withTelegraph(new ChoiceTelegraph(Optional.of(danger), Optional.of(TellAnswer.DODGE),
                        OptionalInt.empty()));
    }
}
