package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ChoiceKind;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wait of the group ledgers holds only the choices that ask for a token (EFO-03, A13): after a refused attack the
 * same mob may still heal, escape or defend on the next tick.
 */
class GroupLedgerWaitTest {
    private static final ChoiceId STRIKE = new ChoiceId("strike");
    private static final ChoiceId LEAP = new ChoiceId("leap_away");
    private static final ChoiceId MEND = new ChoiceId("mend");

    private final EngineFixtures.Sessions sessions = new EngineFixtures.Sessions();
    private final ExecutionFixture.Actions actions = new ExecutionFixture.Actions(sessions.clock);
    private final IntentControl control = new IntentControl(sessions.board, actions,
            new DecisionMetrics(sessions.clock::currentTick), new ExecutionLog(line -> {
            }, false), new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(),
            new DecisionMetrics(sessions.clock::currentTick)), DecisionTap.NONE);

    private static SkillChoice typed(ChoiceId id, SkillRole role, Delivery delivery, DamageTier damage) {
        var reach = delivery == Delivery.SELF ? OptionalDouble.empty() : OptionalDouble.of(3.0);
        return new SkillChoice(id, ChoiceKind.MYTHIC_SKILL, Optional.of("TPV_" + id.value()), Set.of(), true, 0,
                new SkillPurpose(role, Set.of(role), delivery, reach, OptionalDouble.empty(), 0,
                        CooldownClass.UNKNOWN, damage, Set.of(), Set.of(), false, PurposeOrigin.DECLARED));
    }

    private static TacticalProfile profile() {
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        choices.put(STRIKE, typed(STRIKE, SkillRole.DAMAGE, Delivery.MELEE, DamageTier.MEDIUM));
        choices.put(LEAP, typed(LEAP, SkillRole.ESCAPE, Delivery.SELF, DamageTier.NONE));
        choices.put(MEND, typed(MEND, SkillRole.SUSTAIN, Delivery.SELF, DamageTier.NONE));
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        var press = new TacticId("press");
        tactics.put(press, new Tactic(press, Movement.APPROACH, Optional.of(STRIKE), Optional.empty(), 50));
        return new TacticalProfile("ledger_ai", "Ledger", "Defeat the target", tactics, choices, List.of(),
                new MovementSettings(2.5, 5, 9, 16, 10, 1.2, 1.15, OptionalDouble.empty()),
                new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10), List.of(),
                ExecutionFixture.timing(1));
    }

    @Test
    void aRefusedAttackHoldsOnlyTheChoicesThatAskForAToken() {
        var profile = profile();
        var session = sessions.registry.open(EngineFixtures.id(1), profile);
        var tick = 40L;
        session.ledgerWaitUntilTick(tick + 10);
        var strike = profile.choices().get(STRIKE);
        assertEquals(tick + 10, control.readyTick(session, strike, tick), "the attack waits for the ledger");
        assertEquals(tick + 10, control.slackedReady(session, strike, 0, tick));
        assertEquals(tick, control.readyTick(session, profile.choices().get(LEAP), tick),
                "an escape without damage does not wait for a refused attack");
        assertEquals(tick, control.readyTick(session, profile.choices().get(MEND), tick),
                "a heal does not wait for a refused attack");
        assertEquals(tick, control.slackedReady(session, profile.choices().get(LEAP), 1, tick));
    }

    @Test
    void onlyDamagingChoicesWithAPoolAskForAToken() {
        var profile = profile();
        assertTrue(GroupCastGate.asksToken(profile.choices().get(STRIKE)));
        assertFalse(GroupCastGate.asksToken(profile.choices().get(LEAP)));
        assertFalse(GroupCastGate.asksToken(profile.choices().get(MEND)));
        var untyped = new SkillChoice(new ChoiceId("swing"), ChoiceKind.MYTHIC_SKILL, Optional.of("TPV_Swing"),
                Set.of(), true, 0);
        assertTrue(GroupCastGate.asksToken(untyped), "an untyped choice counts as damaging, as the gate reads it");
        var session = sessions.registry.open(EngineFixtures.id(2), profile);
        assertTrue(session.asksToken(0));
        assertFalse(session.asksToken(1));
        assertFalse(session.asksToken(2));
        assertTrue(session.asksToken(-1), "an unknown index keeps the wait, as before");
    }
}
