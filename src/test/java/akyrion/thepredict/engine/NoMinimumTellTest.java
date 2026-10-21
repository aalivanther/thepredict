package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActionStance;
import akyrion.thepredict.domain.BaseStance;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceTelegraph;
import akyrion.thepredict.domain.CombatStance;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DangerClass;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TelegraphPolicy;
import akyrion.thepredict.domain.TelegraphSettings;
import akyrion.thepredict.domain.TellAnswer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A player without a minimum still opens on the author's wind-up and recovers (errata E23-7). */
class NoMinimumTellTest {
    @Test
    void withoutAMinimumTheShownLengthIsTheAuthorWindUpAndTheTellStillOpens() {
        var settings = TelegraphSettings.DEFAULT;
        assertEquals(10, TelegraphPolicy.telegraphTicks(10, DangerClass.SEVERE, TellAnswer.BACK_OFF, false, settings));
        assertEquals(0, TelegraphPolicy.leadTicks(10, 10));
        assertTrue(TelegraphPolicy.opens(true, false, DangerClass.SEVERE, 10));
        assertEquals(CombatStance.WINDUP, CombatStance.visible(BaseStance.NEUTRAL.ordinal(), ActionStance.WINDUP.ordinal()));
    }

    @Test
    void leadZeroClosesInRecoveryInsteadOfNone() {
        long start = 500L;
        var session = open(slam(10, 12), playersOff(), start);
        assertFalse(TellOpening.hold(session, slam(10, 12), Movement.NONE, start, QuietActions.INSTANCE, new long[5]));
        assertEquals(ActionStance.WINDUP, session.actionStance());
        assertEquals(start + 10, session.nextStanceTick());
        assertEquals(MobSession.NEVER, session.releaseTick());
        StanceClock.evaluateBase(session, start + 10, QuietActions.INSTANCE, new long[5]);
        assertEquals(ActionStance.WINDUP, session.actionStance());
        StanceClock.advance(session, start + 10, QuietActions.INSTANCE, new long[5]);
        assertEquals(ActionStance.RECOVERY, session.actionStance());
        assertEquals(start + 22, session.actionUntilTick());
    }

    @Test
    void windUpThatAlreadyCoversTheMinimumStillRecovers() {
        long start = 800L;
        var session = open(slam(40, 12), TelegraphSettings.DEFAULT, start);
        assertFalse(TellOpening.hold(session, slam(40, 12), Movement.NONE, start, QuietActions.INSTANCE, new long[5]));
        assertEquals(0, TelegraphPolicy.leadTicks(session.telegraphTicks(), 40));
        assertEquals(start + 40, session.nextStanceTick());
        StanceClock.evaluateBase(session, start + 40, QuietActions.INSTANCE, new long[5]);
        assertEquals(ActionStance.WINDUP, session.actionStance());
        StanceClock.advance(session, start + 40, QuietActions.INSTANCE, new long[5]);
        assertEquals(ActionStance.RECOVERY, session.actionStance());
        assertEquals(start + 52, session.actionUntilTick());
    }

    private static MobSession open(SkillChoice choice, TelegraphSettings telegraph, long tick) {
        var base = EngineFixtures.profile("Guardian");
        var profile = new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(),
                Map.of(choice.id(), choice), base.contexts(), base.movement(), base.health(), base.threat(),
                base.baseAi(), base.timing(), base.participants(), base.difficulty(), base.stances(), telegraph,
                Optional.empty());
        var session = new MobSession(new MobKey(UUID.randomUUID(), 0), profile);
        session.playerTarget(true);
        session.recordTarget(UUID.randomUUID(), 3);
        session.baseSinceTick(tick);
        return session;
    }

    private static SkillChoice slam(int windUp, int recovery) {
        var id = new ChoiceId("slam");
        var purpose = new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(3),
                OptionalDouble.empty(), windUp, CooldownClass.SHORT, DamageTier.HIGH, Set.of(), Set.of(), false,
                PurposeOrigin.DECLARED);
        return SkillChoice.typed(id, "Slam", Set.of(), true, 0, purpose).withTelegraph(new ChoiceTelegraph(
                Optional.empty(), Optional.of(TellAnswer.BACK_OFF), OptionalInt.of(recovery)));
    }

    private static TelegraphSettings playersOff() {
        var defaults = TelegraphSettings.DEFAULT;
        return new TelegraphSettings(false, defaults.againstEntities(), defaults.light(), defaults.medium(),
                defaults.heavy(), defaults.severe(), defaults.blockTicks(), defaults.dodgeTicks(),
                defaults.backOffTicks(), defaults.breakSightTicks(), defaults.interruptTicks(),
                defaults.deliveryMarginTicks(), defaults.maxReleasesPerTick(), defaults.whiffRecoveryTicks(),
                defaults.whiffCooldownTicks(), defaults.resultWindowTicks(), defaults.commitmentPercent(),
                defaults.entropyCommitmentPercent(), defaults.shortWindUp(), defaults.defaultRecoveryTicks());
    }
}
