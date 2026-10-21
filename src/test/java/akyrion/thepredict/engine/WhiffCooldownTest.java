package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.TacticalProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** A whiff, a lost target, a rejection or a feint holds only that choice (M23.4). */
class WhiffCooldownTest {
    @Test
    void theChoiceWaitsFortyTicksAndTheOtherDoesNot() {
        var first = new ChoiceId("melee");
        var second = new ChoiceId("guard");
        var base = EngineFixtures.profile("Guardian");
        var profile = new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(),
                Map.of(first, SkillChoice.untyped(first, "Melee", Set.of(), true, 0), second,
                        SkillChoice.untyped(second, "Guard", Set.of(), false, 0)),
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing());
        var session = new MobSession(new MobKey(UUID.randomUUID(), 0), profile);
        session.noteWhiff(0, 40);
        assertEquals(40, session.whiffUntil(0));
        assertEquals(Long.MIN_VALUE, session.whiffUntil(1));
        session.openTell(0, 0L, 10, 0, 0, true, UUID.randomUUID());
        session.releaseTick(0L);
        TelegraphRelease.release(session, 8L, QuietActions.INSTANCE, new long[5]);
        assertFalse(session.tellOpen());
        assertEquals(8 + profile.telegraph().whiffCooldownTicks(), session.whiffUntil(0));
    }
}
