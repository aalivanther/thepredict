package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ActionStance;
import akyrion.thepredict.domain.DifficultySettings;
import akyrion.thepredict.domain.ParticipantRules;
import akyrion.thepredict.domain.PoiseSettings;
import akyrion.thepredict.domain.StanceState;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TelegraphSettings;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Damage with the outcome window closed still fills poise and can mark punished (M23.7). */
class ClosedWindowPoiseTest {
    @Test
    void damageWithTheWindowClosedFillsPoiseAndMarksPunished() {
        var attacker = UUID.randomUUID();
        var session = strike(Optional.of(PoiseSettings.defaults()), attacker, 0.05);
        assertEquals(0.05, session.poiseDamage(), 1e-9);
        assertTrue(session.punished());
        assertFalse(session.outcomes().open().isPresent());
    }

    @Test
    void withoutPoiseTheMeterStaysAtZero() {
        var attacker = UUID.randomUUID();
        var session = strike(Optional.empty(), attacker, 0.05);
        assertEquals(0.0, session.poiseDamage(), 0.0);
        assertTrue(session.punished());
    }

    private static MobSession strike(Optional<PoiseSettings> poise, UUID attacker, double fraction) {
        var harness = new EngineFixtures.Sessions();
        var id = UUID.randomUUID();
        harness.registry.open(id, profile(poise));
        var opened = harness.registry.find(id).orElseThrow();
        harness.encounters.assign(opened, UUID.randomUUID(), attacker, key -> false);
        opened.recordTarget(attacker, 2);
        opened.stanceState(StanceState.withAction(StanceState.INITIAL, ActionStance.RECOVERY.ordinal()));
        opened.punishTarget(attacker);
        new OutcomeEvents(harness.registry, harness.encounters, new OutcomeMetrics(), new DecisionMetrics(() -> 0L))
                .takenFrom(id, attacker, fraction, 20);
        return opened;
    }

    private static TacticalProfile profile(Optional<PoiseSettings> poise) {
        var base = EngineFixtures.profile("Guardian");
        return new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), base.tactics(), base.choices(),
                base.contexts(), base.movement(), base.health(), base.threat(), base.baseAi(), base.timing(),
                ParticipantRules.DEFAULT, DifficultySettings.DEFAULT, base.stances(), TelegraphSettings.DEFAULT, poise);
    }
}
