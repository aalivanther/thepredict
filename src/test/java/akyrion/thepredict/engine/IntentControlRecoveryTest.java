package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActionStance;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.StanceState;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decision 6 of M23 in the start guard (E-3): during {@code RECOVERY} the busy deadline holds every attack, and only
 * a non-damaging escape or defense may start before it; one start per tick still holds, and a wind-up never lets
 * anything through.
 */
class IntentControlRecoveryTest {
    private final EngineFixtures.Sessions sessions = new EngineFixtures.Sessions();
    private final IntentControl control = new IntentControl(sessions.board,
            new ExecutionFixture.Actions(sessions.clock), new DecisionMetrics(sessions.clock::currentTick),
            new ExecutionLog(line -> {
            }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(),
            new DecisionMetrics(sessions.clock::currentTick)), DecisionTap.NONE);

    private static SkillChoice typed(String id, SkillRole role, Delivery delivery, DamageTier damage) {
        var reach = delivery == Delivery.SELF ? OptionalDouble.empty() : OptionalDouble.of(3.0);
        return new SkillChoice(new ChoiceId(id), ChoiceKind.MYTHIC_SKILL, Optional.of("TPV_" + id), Set.of(), true, 0,
                new SkillPurpose(role, Set.of(role), delivery, reach, OptionalDouble.empty(), 0, CooldownClass.UNKNOWN,
                        damage, Set.of(), Set.of(), false, PurposeOrigin.DECLARED));
    }

    private MobSession busyIn(ActionStance stance) {
        var session = sessions.open(1);
        session.stanceState(StanceState.withAction(session.stanceState(), stance.ordinal()));
        session.busyUntil(60);
        session.playerTarget(true);
        return session;
    }

    @Test
    void onlyANonDamagingEscapeOrDefenseStartsBeforeTheDeadlineOfARecovery() {
        var session = busyIn(ActionStance.RECOVERY);
        var tick = 50L;
        assertTrue(control.castStartAllowed(session, tick, typed("leap", SkillRole.ESCAPE, Delivery.SELF,
                DamageTier.NONE)), "an escape may start while the mob recovers");
        assertTrue(control.castStartAllowed(session, tick, typed("ward", SkillRole.DEFENSE, Delivery.SELF,
                DamageTier.NONE)));
        assertFalse(control.castStartAllowed(session, tick, typed("strike", SkillRole.DAMAGE, Delivery.MELEE,
                DamageTier.MEDIUM)), "the mob does not attack during its recovery");
        assertFalse(control.castStartAllowed(session, tick), "the plain guard still holds the whole recovery");
        session.noteCastStarted(tick);
        assertFalse(control.castStartAllowed(session, tick, typed("leap", SkillRole.ESCAPE, Delivery.SELF,
                DamageTier.NONE)), "one start per tick");
    }

    @Test
    void aWindUpLetsNothingStartAndTheEndOfTheDeadlineLetsEverythingStart() {
        var session = busyIn(ActionStance.WINDUP);
        var escape = typed("leap", SkillRole.ESCAPE, Delivery.SELF, DamageTier.NONE);
        assertFalse(control.castStartAllowed(session, 50L, escape));
        var strike = typed("strike", SkillRole.DAMAGE, Delivery.MELEE, DamageTier.MEDIUM);
        assertTrue(control.castStartAllowed(session, 60L, strike), "the busy deadline has passed");
    }
}
