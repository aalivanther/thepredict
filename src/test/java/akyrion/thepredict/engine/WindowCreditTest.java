package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.QuestionKey;
import akyrion.thepredict.domain.QuestionSpec;
import akyrion.thepredict.domain.Situation;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.TickSignals;
import akyrion.thepredict.domain.learn.ValueStatDelta;
import akyrion.thepredict.domain.learn.ValueStatKey;
import akyrion.thepredict.engine.learn.LearnedArtifacts;
import akyrion.thepredict.engine.learn.LearnedLocalPick;
import akyrion.thepredict.engine.learn.ValueTableBuilder;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B and PZ-28: the credit of a window has the roles, the source, the week and the live context of its fight. */
class WindowCreditTest {
    private static final long EPOCH = 3 * ValueStatKey.WEEK_MS + 1_000L;
    private static final TacticId BASH = new TacticId("shield_bash");
    private static final TacticId REGEN = new TacticId("regen");

    @Test
    void theRolesOfTheChoiceDecideTheAvoidedDamageNotTheNameOfTheTactic() {
        var session = session();
        session.learning().noteWindow("", 0.01);
        WindowCredit.record(session, summary(BASH, ControlLevel.LOCAL), false, 0L);
        session.learning().noteWindow("", 0.01);
        WindowCredit.record(session, summary(REGEN, ControlLevel.LOCAL), false, 0L);
        var rows = session.learning().credit().onEncounterClosed(session.profile().id(), 0.0, EPOCH);
        assertEquals(0.0, avoided(rows, BASH), 0.0, "a bash that only damages avoids nothing, shield or not");
        assertEquals(0.2, avoided(rows, REGEN), 1.0e-9, "a sustain choice earns (0.01 - 0) x 20 ticks");
    }

    @Test
    void aWindowOfAPlayerFightCarriesItsSourceWeekJevAndLiveContext() {
        var session = session();
        session.playerTarget(true);
        session.tickSignals(TickSignals.pack(TickSignals.PRESENT | (1L << TickSignals.HAS_TARGET), 4, 0, 0, 0, 100,
                10));
        session.learning().noteWindow(WindowCredit.live(session).text(), session.learning().damage().rate(0L));
        WindowCredit.record(session, summary(REGEN, ControlLevel.JEV), true, EPOCH);
        var row = session.learning().credit().onEncounterClosed(session.profile().id(), 0.0, EPOCH).getFirst();
        assertEquals("player_full", row.key().source());
        assertEquals(ValueStatKey.week(EPOCH), row.key().week());
        assertTrue(row.key().week() > 0);
        assertEquals(1, row.jevSeen());
        var fields = row.key().context().split(":");
        assertEquals("LOW", fields[3], "the target health of the live signals, not a fixed band");
        assertEquals("PLAYER", fields[4]);
    }

    @Test
    void aJevWindowAgreesOnlyWhenItsTacticIsTheLeaderOfTheCaptureItAnswered() {
        var session = session();
        var builder = new ValueTableBuilder();
        builder.add(1, LearnedLocalPick.tacticCode(REGEN.value()), 3, 7L, 1.0, 0.01, 40, 1.0);
        builder.add(1, LearnedLocalPick.tacticCode(BASH.value()), 3, 7L, 0.1, 0.01, 40, 1.0);
        session.learning().stage(builder.build(), LearnedArtifacts.State.ACTIVE, 1, 7L);
        session.learning().activateIfReady();
        var encounter = new Encounter(new EncounterId(1), new UUID(0, 1), 0);
        encounter.addMember(session);
        var question = new QuestionSpec(QuestionKey.of(0), session.key(), Situation.NOW, List.of(BASH, REGEN),
                Optional.empty());
        var plan = new EncounterQuestions.Plan(List.of(question), Map.of(session.key(), session.profile()), List.of());
        var outcomes = outcomes();
        CaptureGate.apply(encounter, plan, 4L);
        outcomes.adopted(session, intent(session, REGEN, ControlLevel.JEV), 5L, false);
        CaptureGate.apply(encounter, new EncounterQuestions.Plan(List.of(), Map.of(), List.of()), 6L);
        outcomes.close(session, 10L);
        CaptureGate.apply(encounter, plan, 11L);
        outcomes.adopted(session, intent(session, BASH, ControlLevel.JEV), 11L, false);
        outcomes.close(session, 20L);
        outcomes.adopted(session, intent(session, REGEN, ControlLevel.LOCAL), 21L, false);
        outcomes.close(session, 30L);
        outcomes.adopted(session, intent(session, REGEN, ControlLevel.JEV), 31L, true);
        outcomes.close(session, 40L);
        var rows = session.learning().credit().onEncounterClosed(session.profile().id(), 0.0, EPOCH);
        assertEquals(4, rows.size());
        assertEquals(1, rows.get(0).jevAgree(), "Jev chose the leader of the capture it answered, though a later "
                + "capture without a question noted none");
        assertEquals(0, rows.get(1).jevAgree(), "the bash is not the leader");
        assertEquals(1, rows.get(1).jevSeen());
        assertEquals(0, rows.get(2).jevSeen() + rows.get(2).jevAgree(), "a local window never asked Jev");
        assertEquals(1, rows.get(3).jevSeen());
        assertEquals(0, rows.get(3).jevAgree(), "a contingency answers another question than the NOW one");
    }

    @Test
    void withoutAnActiveTableNoWindowAgrees() {
        var session = session();
        var encounter = new Encounter(new EncounterId(1), new UUID(0, 1), 0);
        encounter.addMember(session);
        var question = new QuestionSpec(QuestionKey.of(0), session.key(), Situation.NOW, List.of(BASH, REGEN),
                Optional.empty());
        CaptureGate.apply(encounter, new EncounterQuestions.Plan(List.of(question),
                Map.of(session.key(), session.profile()), List.of()), 4L);
        var outcomes = outcomes();
        outcomes.adopted(session, intent(session, REGEN, ControlLevel.JEV), 5L, false);
        outcomes.close(session, 10L);
        var row = session.learning().credit().onEncounterClosed(session.profile().id(), 0.0, EPOCH).getFirst();
        assertEquals(1, row.jevSeen());
        assertEquals(0, row.jevAgree());
    }

    private static OutcomeEvents outcomes() {
        var fixture = new EngineFixtures.Sessions();
        return new OutcomeEvents(fixture.registry, fixture.encounters, new OutcomeMetrics(),
                new DecisionMetrics(() -> 0L));
    }

    private static TacticalIntent intent(MobSession session, TacticId tactic, ControlLevel level) {
        return new TacticalIntent(session.key(), tactic, Movement.HOLD, Optional.empty(), 0.8, List.of(), Map.of(),
                level, 0, Long.MAX_VALUE, false);
    }

    private static double avoided(List<ValueStatDelta> rows, TacticId tactic) {
        return rows.stream().filter(row -> row.key().tactic().equals(tactic.value())).mapToDouble(
                ValueStatDelta::avoidedSum).sum();
    }

    private static OutcomeWindow.Summary summary(TacticId tactic, ControlLevel level) {
        return new OutcomeWindow.Summary(level, tactic, Movement.HOLD, 0, 20, 0, 0, false, 0, 0, false, false, 0.1);
    }

    private static MobSession session() {
        var bash = choice("bash", SkillRole.DAMAGE);
        var heal = choice("heal", SkillRole.SUSTAIN);
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        tactics.put(BASH, new Tactic(BASH, Movement.HOLD, Optional.of(bash.id()), Optional.empty(), 50));
        tactics.put(REGEN, new Tactic(REGEN, Movement.HOLD, Optional.of(heal.id()), Optional.empty(), 40));
        var base = EngineFixtures.profile("Guardian");
        var profile = new TacticalProfile(base.id(), base.mythicMobId(), base.objective(), tactics,
                Map.of(bash.id(), bash, heal.id(), heal), base.contexts(), base.movement(), base.health(),
                base.threat(), base.baseAi(), base.timing());
        return new MobSession(new MobKey(UUID.randomUUID(), 0), profile);
    }

    private static SkillChoice choice(String id, SkillRole role) {
        var purpose = new SkillPurpose(role, Set.of(role), Delivery.SELF, OptionalDouble.empty(), OptionalDouble.empty(),
                0, CooldownClass.SHORT, role == SkillRole.DAMAGE ? DamageTier.MEDIUM : DamageTier.NONE, Set.of(),
                Set.of(), false, PurposeOrigin.DECLARED);
        return SkillChoice.typed(new ChoiceId(id), "TPV_" + id, Set.of(), true, 0, purpose);
    }
}
