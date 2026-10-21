package akyrion.thepredict.engine;

import akyrion.thepredict.domain.CandidatePolicy;
import akyrion.thepredict.domain.CandidateScoring;
import akyrion.thepredict.domain.CastMode;
import akyrion.thepredict.domain.ChoiceId;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.CooldownClass;
import akyrion.thepredict.domain.DamageTier;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.Delivery;
import akyrion.thepredict.domain.DistanceBand;
import akyrion.thepredict.domain.EnemyPattern;
import akyrion.thepredict.domain.ExecutionOutcome;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.HealthThresholds;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.MobProbe;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.MovementSettings;
import akyrion.thepredict.domain.PatternExploit;
import akyrion.thepredict.domain.PurposeOrigin;
import akyrion.thepredict.domain.SkillChoice;
import akyrion.thepredict.domain.SkillPurpose;
import akyrion.thepredict.domain.SkillRole;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.TacticalIntent;
import akyrion.thepredict.domain.TacticalProfile;
import akyrion.thepredict.domain.ThreatWeights;
import akyrion.thepredict.domain.UseCondition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cast of the choice {@code raisedTactic} stored in the intent counts as motivated (M26.5). Ranking alone does
 * not: the launch goes through {@code tryIntent} or {@link IntentControl#cast}, and the count is the launchable
 * subset, not the eligible set whose leader is still reloading.
 */
class IntentPatternCastTest {
    private static final ChoiceId LEAD = new ChoiceId("lead");
    private static final ChoiceId MID = new ChoiceId("mid");
    private static final ChoiceId HIT = new ChoiceId("hit");
    private static final TacticId LEAD_TACTIC = new TacticId("keep_lead");
    private static final TacticId MID_TACTIC = new TacticId("keep_mid");
    private static final TacticId HIT_TACTIC = new TacticId("hold_hit");

    @Test
    void anAnyReadyLaunchOfTheRaisedIntentChoiceIsMotivated() {
        var heard = launch(CastMode.ANY_READY, HIT);
        assertEquals(List.of("hit"), heard.casts);
        assertEquals(1, heard.session.patternMotivated());
        assertTrue(heard.exploited.isEmpty(), "the event waits for the result of its cast (E-10)");
        heard.control.settleExploit(heard.session, 1 + OutcomeWindow.HIT_WINDOW_TICKS);
        assertEquals(List.of("hit"), heard.exploited);
    }

    @Test
    void anIntentOnlyLaunchOfTheRaisedIntentChoiceIsMotivated() {
        var heard = launch(CastMode.INTENT_ONLY, HIT);
        assertEquals(List.of("hit"), heard.casts);
        assertEquals(1, heard.session.patternMotivated());
        assertTrue(heard.exploited.isEmpty(), "the event waits for the result of its cast (E-10)");
        heard.control.settleExploit(heard.session, 1 + OutcomeWindow.HIT_WINDOW_TICKS);
        assertEquals(List.of("hit"), heard.exploited);
    }

    @Test
    void aChoiceTheFactorDidNotPutFirstIsNotMotivated() {
        var heard = launch(CastMode.ANY_READY, MID);
        assertEquals(List.of("mid"), heard.casts);
        assertTrue(heard.exploited.isEmpty());
        assertEquals(0, heard.session.patternMotivated());
    }

    /**
     * E-10: the event of a motivated cast waits until the result of that same cast settles, and then carries it: a hit
     * within the window settles it as landed, the next cast settles a missed one, and the end of the window settles the
     * last one without waiting for another cast.
     */
    @Test
    void theExploitEventCarriesTheResultOfItsOwnCastOnceItSettles() {
        var heard = launch(CastMode.ANY_READY, HIT);
        assertEquals(List.of(), heard.landed, "the first motivated cast waits for its result");
        heard.outcomes.dealt(heard.session.key().entityId(), EngineFixtures.player(1), 0.1, 1);
        assertEquals(List.of(true), heard.landed, "the hit within the window settles its own cast as landed");
        heard.again(2);
        heard.control.settleExploit(heard.session, 3);
        assertEquals(List.of(true), heard.landed, "a cast inside its window keeps waiting");
        heard.again(4);
        assertEquals(List.of(true, false), heard.landed, "a cast without a hit is settled by the next cast");
        heard.control.settleExploit(heard.session, 5 + OutcomeWindow.HIT_WINDOW_TICKS);
        assertEquals(List.of(true, false, false), heard.landed, "the last cast settles once its window passed");
        assertEquals(List.of(HIT.value(), HIT.value(), HIT.value()), heard.exploited);
        heard.control.settleExploit(heard.session, 6 + OutcomeWindow.HIT_WINDOW_TICKS);
        assertEquals(3, heard.landed.size(), "each event is emitted once");
    }

    private static Heard launch(CastMode mode, ChoiceId choice) {
        var profile = profile(mode);
        assertScene(profile);
        var heard = new Heard();
        var sessions = new EngineFixtures.Sessions();
        var metrics = new DecisionMetrics(sessions.clock::currentTick);
        var gate = new Gate();
        var outcomes = new OutcomeEvents(sessions.registry, sessions.encounters, new OutcomeMetrics(), metrics);
        outcomes.tapTo(heard);
        var control = new IntentControl(sessions.board, gate, metrics, new ExecutionLog(line -> {
        }, false), outcomes, heard);
        var session = sessions.registry.open(EngineFixtures.id(1), profile);
        arm(session, profile, choice);
        control.castIfReady(session, 0, 0);
        heard.session = session;
        heard.control = control;
        heard.outcomes = outcomes;
        heard.choice = profile.choices().get(choice);
        heard.gate = gate;
        heard.casts = List.copyOf(gate.casts);
        return heard;
    }

    private static void assertScene(TacticalProfile profile) {
        var tactics = List.copyOf(profile.tactics().values());
        var mask = EnemyPattern.SPAMS_CLICKS.bit();
        var full = CandidatePolicy.rank(CandidateScoring.UTILITY, profile, tactics, Set.of(), mask);
        assertFalse(full.raisedByPattern());
        assertEquals(LEAD_TACTIC, full.order().get(0));
        var launchable = List.of(tactics.get(1), tactics.get(2));
        var raised = CandidatePolicy.rank(CandidateScoring.UTILITY, profile, launchable, Set.of(), mask);
        assertTrue(raised.raisedByPattern());
        assertEquals(HIT_TACTIC, raised.order().get(0));
        var probed = CandidatePolicy.rank(CandidateScoring.UTILITY, profile, launchable,
                Set.of(UseCondition.TARGET_IN_MELEE), mask);
        assertFalse(probed.raisedByPattern());
    }

    private static void arm(MobSession session, TacticalProfile profile, ChoiceId choice) {
        var tactic = profile.tactics().values().stream()
                .filter(found -> choice.equals(found.choice().orElse(null))).findFirst().orElseThrow();
        session.adoptIntent(new TacticalIntent(session.key(), tactic.id(), tactic.movement(), tactic.choice(), 0.0,
                List.of(), Map.of(), ControlLevel.LOCAL, 0, Long.MAX_VALUE / 2, false));
        var eligible = new LinkedHashSet<>(profile.tactics().keySet());
        session.facts(new MobFacts(session.key(), HealthBand.HEALTHY, Optional.empty(), Set.of(), eligible, Set.of(),
                Set.of(), Map.of(), eligible));
        session.noteProbe(new MobProbe(session.key(), EngineFixtures.WORLD, HealthBand.HEALTHY,
                Optional.of(EngineFixtures.player(1)), Optional.of(DistanceBand.MELEE)), 0);
        session.recordTarget(EngineFixtures.player(1), 2.0);
        session.candidateScoring(CandidateScoring.UTILITY);
        session.patternHeld(EnemyPattern.SPAMS_CLICKS.bit());
        session.patternFocus(EnemyPattern.SPAMS_CLICKS.ordinal());
        session.scheduleReady(0);
    }

    private static TacticalProfile profile(CastMode mode) {
        var timing = new DecisionTiming(20, 20, 100, 0.5, 0.2, 1).withCasting(mode, 0);
        var tactics = new LinkedHashMap<TacticId, Tactic>();
        tactics.put(LEAD_TACTIC, new Tactic(LEAD_TACTIC, Movement.KEEP_DISTANCE, Optional.of(LEAD), Optional.empty(),
                60));
        tactics.put(MID_TACTIC, new Tactic(MID_TACTIC, Movement.KEEP_DISTANCE, Optional.of(MID), Optional.empty(), 50));
        tactics.put(HIT_TACTIC, new Tactic(HIT_TACTIC, Movement.HOLD, Optional.of(HIT), Optional.empty(), 46));
        var choices = new LinkedHashMap<ChoiceId, SkillChoice>();
        choices.put(LEAD, SkillChoice.typed(LEAD, "TPV_Lead", Set.of(), false, 0, plain(Set.of())));
        choices.put(MID, SkillChoice.typed(MID, "TPV_Mid", Set.of(), false, 0, plain(Set.of(UseCondition.TARGET_IN_MELEE))));
        choices.put(HIT, SkillChoice.typed(HIT, "TPV_Hit", Set.of(), false, 0, melee()));
        return new TacticalProfile("pattern_ai", "Guardian", "Defeat the target", tactics, choices, List.of(),
                MovementSettings.DEFAULTS, new HealthThresholds(0.3, 0.6), new ThreatWeights(1, 1, 1, 0.5, 10),
                List.of(), timing);
    }

    private static SkillPurpose plain(Set<UseCondition> bestWhen) {
        return new SkillPurpose(SkillRole.CONTROL, Set.of(SkillRole.CONTROL), Delivery.SELF, OptionalDouble.empty(),
                OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.NONE, bestWhen, Set.of(), false,
                PurposeOrigin.DECLARED);
    }

    private static SkillPurpose melee() {
        return new SkillPurpose(SkillRole.DAMAGE, Set.of(SkillRole.DAMAGE), Delivery.MELEE, OptionalDouble.of(3),
                OptionalDouble.empty(), 0, CooldownClass.UNKNOWN, DamageTier.MEDIUM, Set.of(), Set.of(), false,
                PurposeOrigin.DECLARED);
    }

    private static final class Heard implements DecisionTap {
        final List<String> exploited = new ArrayList<>();
        final List<Boolean> landed = new ArrayList<>();
        List<String> casts = List.of();
        MobSession session;
        IntentControl control;
        OutcomeEvents outcomes;
        SkillChoice choice;
        Gate gate;

        @Override
        public void patternExploited(MobKey mob, UUID participant, PatternExploit exploit, String choice, String tactic,
                                     int quotaUsed) {
            exploited.add(choice);
            landed.add(exploit.landed());
        }

        /** Fires the armed choice again on {@code tick}, as the intent path does once its cooldown ends. */
        void again(long tick) {
            gate.cooling.remove(choice.id());
            ReadyCasts.noteRaised(control, session, session.intent().orElseThrow(), choice, tick);
        }
    }

    private static final class Gate implements CombatActions {
        final List<String> casts = new ArrayList<>();
        final Set<ChoiceId> cooling = new LinkedHashSet<>(Set.of(LEAD));

        @Override
        public ExecutionOutcome cast(MobKey mob, SkillChoice choice, Movement movement) {
            cooling.add(choice.id());
            casts.add(choice.id().value());
            return ExecutionOutcome.CAST;
        }

        @Override
        public boolean ready(MobKey mob, SkillChoice choice) {
            return !cooling.contains(choice.id());
        }

        @Override
        public void signalTactic(MobKey mob, TacticId tactic) {
        }

        @Override
        public void addThreat(MobKey mob, UUID participantId, double amount) {
        }
    }
}
