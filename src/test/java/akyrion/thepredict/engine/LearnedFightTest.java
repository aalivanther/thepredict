package akyrion.thepredict.engine;

import akyrion.thepredict.domain.ArmorBand;
import akyrion.thepredict.domain.BehaviorPolicy;
import akyrion.thepredict.domain.CandidateScoring;
import akyrion.thepredict.domain.CombatFacts;
import akyrion.thepredict.domain.ControlLevel;
import akyrion.thepredict.domain.DecisionResult;
import akyrion.thepredict.domain.DecisionTiming;
import akyrion.thepredict.domain.EncounterId;
import akyrion.thepredict.domain.FailureKind;
import akyrion.thepredict.domain.GroupMode;
import akyrion.thepredict.domain.HealthBand;
import akyrion.thepredict.domain.LearningSettings;
import akyrion.thepredict.domain.MobFacts;
import akyrion.thepredict.domain.MobKey;
import akyrion.thepredict.domain.Movement;
import akyrion.thepredict.domain.ParticipantAlias;
import akyrion.thepredict.domain.ParticipantFacts;
import akyrion.thepredict.domain.ParticipantKind;
import akyrion.thepredict.domain.PatternMode;
import akyrion.thepredict.domain.QuestionDetail;
import akyrion.thepredict.domain.Tactic;
import akyrion.thepredict.domain.TacticId;
import akyrion.thepredict.domain.learn.CalibrationKey;
import akyrion.thepredict.domain.learn.CalibrationTarget;
import akyrion.thepredict.domain.learn.ConfidenceGate;
import akyrion.thepredict.domain.learn.ExploreArm;
import akyrion.thepredict.domain.learn.ExplorePolicy;
import akyrion.thepredict.domain.learn.ExploreSettings;
import akyrion.thepredict.domain.learn.LearnCause;
import akyrion.thepredict.domain.learn.OptionsBand;
import akyrion.thepredict.domain.learn.PlattMath;
import akyrion.thepredict.domain.learn.QuestionKind;
import akyrion.thepredict.engine.learn.ConfidenceCalibrator;
import akyrion.thepredict.engine.learn.LearnedArtifacts;
import akyrion.thepredict.engine.learn.LearnedLocalPick;
import akyrion.thepredict.engine.learn.ValueTable;
import akyrion.thepredict.engine.learn.ValueTableBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static akyrion.thepredict.engine.ExecutionFixture.GUARD;
import static akyrion.thepredict.engine.ExecutionFixture.KEEP;
import static akyrion.thepredict.engine.ExecutionFixture.PRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The learned queries, the capture arm and the shadow hint, executed rather than read from source. */
class LearnedFightTest {
    @Test
    void twoQueriesMoveOnlyTheLocalPickAndTheFirstCastSettlesExtra() {
        var observed = exploit(PatternMode.OBSERVE, false);
        assertEquals(KEEP, observed.fixture.view(1).tactic());
        assertEquals("", observed.fixture.session(1).learning().learnedExtra());
        assertTrue(observed.sources.isEmpty());
        var biased = exploit(PatternMode.BIAS, false);
        assertEquals(KEEP, biased.fixture.client.requests.getFirst().questions().getFirst().options().getFirst());
        assertEquals(PRESS, biased.fixture.view(1).tactic());
        assertEquals("learned", biased.fixture.session(1).learning().learnedSource());
        assertTrue(biased.fixture.session(1).learning().learnedExtra().contains("landed"));
        assertEquals(List.of("learned"), biased.sources);
        var tick = (int) biased.decisionTick;
        assertTrue(biased.fixture.session(1).exploitBudget().motivated(tick, 1) >= 1);
        assertEquals(0, biased.fixture.session(1).exploitBudget().motivated(tick + 2, 1));
        var blocked = exploit(PatternMode.BIAS, true);
        assertEquals("", blocked.fixture.session(1).learning().learnedExtra());
        assertEquals(0, blocked.fixture.session(1).exploitBudget().motivated((int) blocked.decisionTick, 1));
    }

    @Test
    void theCapStoredByThePatternStepDecidesWhetherAQueryMayExploit() {
        assertEquals(akyrion.thepredict.domain.PatternSettings.DEFAULT.maxExploitedPatterns(),
                new ExecutionFixture().open(1).patternVariety(), "until the pattern step stores one, the delivered cap");
        var narrowed = exploit(PatternMode.BIAS, 1, 1);
        assertEquals("", narrowed.fixture.session(1).learning().learnedExtra(), "one open pattern fills a cap of one");
        assertTrue(narrowed.sources.isEmpty());
        var lifted = exploit(PatternMode.BIAS, 2, 3);
        assertTrue(lifted.fixture.session(1).learning().learnedExtra().contains("landed"),
                "two open patterns leave room under a cap of three: " + lifted.fixture.session(1).learning()
                        .learnedExtra());
        assertEquals(List.of("learned"), lifted.sources);
        assertThrows(IllegalArgumentException.class, () -> new ExecutionFixture().open(1).patternVariety(0));
    }

    /** LD-33: a moved learned pick names the held pattern of lowest ordinal, not the held mask read as an ordinal. */
    @Test
    void aMovedLearnedPickNamesTheHeldPatternOfLowestOrdinal() {
        var fixture = new ExecutionFixture();
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        var session = fixture.session(1);
        session.candidateScoring(CandidateScoring.LEARNED);
        session.patternMode(PatternMode.BIAS);
        session.patternWindow(1);
        session.learning().usePatternContext(9L);
        session.patternHeld(akyrion.thepredict.domain.EnemyPattern.PREDICTABLE_STRAFE_LEFT.bit()
                | akyrion.thepredict.domain.EnemyPattern.SHIELD_LATE.bit());
        fixture.coordinator.offerTable(table(), LearnedArtifacts.State.ACTIVE, "PASS", "all", 1, 7L);
        var patterns = new ArrayList<akyrion.thepredict.domain.EnemyPattern>();
        fixture.tap.attach(new DecisionTap() {
            @Override
            public void patternExploited(MobKey mob, akyrion.thepredict.domain.PatternExploit exploit) {
                patterns.add(exploit.pattern());
            }
        }, () -> {
        });
        fixture.runUntilRequest(1);
        var tick = fixture.clock.currentTick();
        session.noteCastQuota((int) tick, false);
        session.recordInstall(new akyrion.thepredict.domain.InstallResult.Installed(3), 1L);
        session.facts(facts(session));
        fixture.client.futures.getFirst().complete(new DecisionResult.Failed(FailureKind.RATE_LIMITED,
                Optional.of(Duration.ofSeconds(30)), "rate limited"));
        fixture.run(2);
        assertEquals(List.of(akyrion.thepredict.domain.EnemyPattern.PREDICTABLE_STRAFE_LEFT), patterns);
    }

    @Test
    void captureArmsTheStratumBeforeTheQuestionAndShadowStillAsks() {
        var seeded = seed();
        var encounter = encounter(false);
        var session = first(encounter);
        session.reseed(seeded);
        var plan = plan(session);
        var dropped = CaptureGate.apply(encounter, plan, 4L, learning(true, "off", "off", ExploreSettings.DEFAULT),
                DecisionTap.NONE);
        assertTrue(dropped.questions().isEmpty());
        var choice = session.learning().exploreChoice().orElseThrow();
        assertTrue(choice.inStratum());
        assertEquals(choice.propensities()[choice.index()], choice.propensity(), 1.0e-12);
        assertTrue(choice.propensity() < 1.0);
        assertEquals(plan.questions().getFirst().options().get(choice.index()), session.resolvedTactic().orElseThrow());
        session.learning().noteAcceptedJev();
        assertFalse(CaptureGate.apply(encounter, plan, 4L, learning(true, "off", "off", ExploreSettings.DEFAULT),
                DecisionTap.NONE).questions().isEmpty());
        var quiet = encounter(false);
        var quietSession = first(quiet);
        quietSession.reseed(seeded);
        var stayed = CaptureGate.apply(quiet, plan(quietSession), 4L,
                learning(true, "off", "off", new ExploreSettings(0.0, 0.2)), DecisionTap.NONE);
        assertFalse(stayed.questions().isEmpty());
        assertEquals(1.0, quietSession.learning().exploreChoice().orElseThrow().propensity(), 0.0);
        var joined = encounter(true);
        var causes = new ArrayList<LearnCause>();
        CaptureGate.apply(joined, plan(first(joined)), 4L,
                learning(true, "off", "off", ExploreSettings.DEFAULT), new DecisionTap() {
                    @Override
                    public void learnEvent(String sha256, String event, LearnCause cause) {
                        causes.add(cause);
                    }
                });
        assertEquals(List.of(LearnCause.PLAYER_JOINED), causes);
        assertFalse(first(joined).learning().exploreChoice().isPresent());
        var training = encounter(true);
        first(training).learning().markTraining(true);
        first(training).reseed(seeded);
        assertTrue(CaptureGate.apply(training, plan(first(training)), 4L,
                learning(true, "shadow", "off", ExploreSettings.DEFAULT), DecisionTap.NONE).questions().isEmpty());
        var shadow = encounter(false);
        var shadowSession = first(shadow);
        shadowSession.learning().stage(ValueTable.empty(), LearnedArtifacts.State.ACTIVE, 1, 0L);
        var asked = CaptureGate.apply(shadow, plan(shadowSession), 4L,
                learning(false, "shadow", "off", ExploreSettings.DEFAULT), DecisionTap.NONE);
        assertFalse(asked.questions().isEmpty());
        assertFalse(shadowSession.learning().wouldSkip());
        assertTrue(shadowSession.learning().held().isEmpty());
    }

    @Test
    void dispatchExecutesTheSampledTacticAfterPruning() {
        for (var seed = 1L; seed < 80L; seed++) {
            var fixture = new ExecutionFixture();
            fixture.ready();
            fixture.open(1, ExecutionFixture.profile(new DecisionTiming(4, 4, 100, 0.5, 0.2, 0), 0));
            fixture.target(1, 1);
            var session = fixture.session(1);
            session.recordInstall(new akyrion.thepredict.domain.InstallResult.Installed(3), 1L);
            session.learning().markTraining(true);
            session.reseed(seed);
            fixture.coordinator.useLearning(learning(true, "off", "off", new ExploreSettings(0.3, 0.5)));
            var explored = new int[] {-1};
            var policy = new BehaviorPolicy[] {null};
            fixture.tap.attach(new DecisionTap() {
                @Override
                public void explored(MobKey mob, BehaviorPolicy chosenPolicy, int chosen) {
                    explored[0] = chosen;
                    policy[0] = chosenPolicy;
                }
            }, () -> {
            });
            TacticId sampled = null;
            var probeAtSettle = -1L;
            for (var tick = 0; tick < 60 && sampled == null; tick++) {
                fixture.run(1);
                var choice = session.learning().exploreChoice();
                if (choice.isEmpty()) {
                    continue;
                }
                var picked = choice.get();
                var options = session.learning().exploreOptions();
                if (!picked.inStratum() || picked.index() <= 0 || picked.index() >= options.size()) {
                    continue;
                }
                var candidate = options.get(picked.index());
                if (PRESS.equals(candidate) || policy[0] != BehaviorPolicy.EXPLORE_UNIFORM
                        || explored[0] != picked.index()) {
                    continue;
                }
                sampled = candidate;
                probeAtSettle = session.nextProbeTick();
            }
            if (sampled == null) {
                continue;
            }
            for (var step = 0; step < 3 && session.nextProbeTick() == probeAtSettle; step++) {
                fixture.run(1);
            }
            assertEquals(sampled, fixture.view(1).tactic());
            assertEquals(ControlLevel.LOCAL, fixture.view(1).level());
            return;
        }
        throw new AssertionError("seed");
    }

    @Test
    void calibratedInstallUsesStarAndNotTheRawFloor() {
        var below = gate(0.49);
        assertEquals(0.5, below.session(1).learning().confidenceFloor(0.5), 1.0e-9);
        assertNotEquals(ControlLevel.JEV, below.view(1).level());
        var above = gate(0.50);
        assertEquals(0.5, above.session(1).learning().confidenceFloor(0.5), 1.0e-9);
        assertEquals(ControlLevel.JEV, above.view(1).level());
    }

    @Test
    void anAcceptedJevAnswerIsNotReplaced() {
        var fixture = new ExecutionFixture();
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        var session = fixture.session(1);
        session.learning().armExplore(new ExploreArm(3L, 0, 4, 0.1, 0, 0, 0.2, false, BehaviorPolicy.JEV_ARGMAX,
                false));
        fixture.runAnswering(40, ExecutionFixture.nowAndContingency(ExecutionFixture.prefer(KEEP, 0.7, 0.9),
                ExecutionFixture.prefer(KEEP, 0.7, 0.9)));
        assertEquals(ControlLevel.JEV, fixture.view(1).level());
        assertTrue(session.learning().explore().acceptedJev());
    }

    @Test
    void weakCellsCompeteAndWeightsStayInsideTheLocalPick() {
        var builder = new ValueTableBuilder();
        builder.add(1, LearnedLocalPick.tacticCode(PRESS.value()), 3, 7L, 0.0, 0.01, 40, 1.0);
        builder.add(1, LearnedLocalPick.tacticCode(GUARD.value()), 3, 7L, 1.0, 0.01, 40, 1.0);
        var tactics = List.of(tactic(PRESS, 50), tactic(KEEP, 40), tactic(GUARD, 10));
        var lead = LearnedLocalPick.lead(builder.build(), 1, tactics, 7L, 20, CandidateScoring.PRIORITY, Set.of(),
                1.0);
        assertEquals(KEEP, lead.orElseThrow());
        var weights = new double[] {1.0, 1.1, 1.0};
        var equal = List.of(tactic(new TacticId("alpha_cut"), 50), tactic(new TacticId("bravo_cut"), 50),
                tactic(new TacticId("charlie_cut"), 50));
        var valued = new ValueTableBuilder();
        valued.add(1, LearnedLocalPick.tacticCode("alpha_cut"), 3, 7L, 1.0, 0.01, 40, 1.0);
        valued.add(1, LearnedLocalPick.tacticCode("bravo_cut"), 3, 7L, 0.99, 0.01, 40, 1.0);
        valued.add(1, LearnedLocalPick.tacticCode("charlie_cut"), 3, 7L, 0.0, 0.01, 40, 1.0);
        var table = valued.build();
        assertEquals(new TacticId("alpha_cut"), LearnedLocalPick.lead(table, 1, equal, 7L, 20,
                CandidateScoring.PRIORITY, Set.of(), 1.0).orElseThrow());
        assertEquals(new TacticId("bravo_cut"), LearnedLocalPick.lead(table, 1, equal, 7L, 20,
                CandidateScoring.PRIORITY, Set.of(), weights).orElseThrow());
        var fixture = new ExecutionFixture();
        var session = fixture.open(1);
        session.candidateScoring(CandidateScoring.LEARNED);
        session.difficulty().resolve(1L, true, 0, 0, 8, null, akyrion.thepredict.domain.DifficultyResolution.ABSENT,
                null, null, null, Boolean.TRUE, false, akyrion.thepredict.domain.DifficultyResolution.ABSENT);
        session.adoptIntent(new akyrion.thepredict.domain.TacticalIntent(session.key(), PRESS, Movement.APPROACH,
                Optional.of(ExecutionFixture.STRIKE), 0.8, List.of(), java.util.Map.of(), ControlLevel.JEV, 0,
                Long.MAX_VALUE, false));
        session.learning().noteLearned("learned", akyrion.thepredict.engine.learn.LearnedResult.NEUTRAL);
        LearnedCast.settle(session, session.profile().choices().get(ExecutionFixture.STRIKE),
                akyrion.thepredict.domain.ExecutionOutcome.CAST);
        var pressed = session.learning().localWeights(true, List.of(PRESS.value(), KEEP.value()));
        assertNotEquals(1.0, pressed[0], 1.0e-9, "the weight of the tactic that cast moves");
        assertTrue(pressed[0] <= 1.1);
        assertEquals(1.0, pressed[1], 0.0, "another tactic keeps its weight whatever its place in the list");
        CreditClose.finish(session, LearningSettings.DEFAULT, GroupMode.OFF, 1L);
        assertEquals(1.0, session.learning().localWeights(true, List.of(PRESS.value()))[0], 0.0);
        session.learning().markTraining(true);
        session.learning().observeWeight(true, true, 0, 1.0, 0.0);
        CreditClose.finish(session, LearningSettings.DEFAULT, GroupMode.OFF, 2L);
        assertNotEquals(1.0, session.learning().localWeight(true), 1.0e-9);
        session.learning().observeWeight(false, true, 0, 1.0, 0.0);
    }

    @Test
    void anActiveTableReordersTheQuestionAndShadowKeepsTheBody() {
        var active = ask(LearnedArtifacts.State.ACTIVE, true);
        assertEquals(KEEP, active.fixture.client.requests.getFirst().questions().getFirst().options().getFirst());
        assertEquals("", active.firstVoi);
        assertEquals("would_skip", active.voi);
        assertFalse(active.fixture.client.requests.get(1).questions().isEmpty());
        assertTrue(active.hint.contains("has worked best here"));
        assertFalse(active.hint.isEmpty());
        assertTrue(active.bodies.stream().noneMatch(body -> body.contains("has worked best")));
        var pending = ask(LearnedArtifacts.State.PENDING_VERDICT, false);
        assertEquals(PRESS, pending.fixture.client.requests.getFirst().questions().getFirst().options().getFirst());
        assertEquals("", pending.hint);
    }

    private static ExecutionFixture gate(double confidence) {
        var fixture = new ExecutionFixture();
        var key = new CalibrationKey("jev-1.13.0", QuestionDetail.FULL, QuestionKind.NOW, CalibrationTarget.ACCEPTABLE,
                OptionsBand.TWO);
        var fit = new PlattMath.Fit(0.833, 1.417, false, Optional.empty());
        var calibrator = new ConfidenceCalibrator("jev-1.13.0", new CalibrationKey[] {key}, new PlattMath.Fit[] {fit});
        fixture.coordinator.useCalibrator(ConfidenceGate.CALIBRATED, calibrator, key, "jev-1.13.0", Double.NaN);
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        fixture.runAnswering(12, ExecutionFixture.nowAndContingency(ExecutionFixture.prefer(PRESS, 0.7, confidence),
                ExecutionFixture.prefer(ExecutionFixture.FALL_BACK, 0.7, 0.9)));
        return fixture;
    }

    private static Run exploit(PatternMode mode, boolean full) {
        return exploit(mode, full ? 2 : 0, 0);
    }

    private static Run exploit(PatternMode mode, int openPatterns, int variety) {
        var fixture = new ExecutionFixture();
        fixture.ready();
        fixture.open(1);
        fixture.target(1, 1);
        var session = fixture.session(1);
        session.candidateScoring(CandidateScoring.LEARNED);
        session.patternMode(mode);
        session.patternWindow(1);
        session.learning().usePatternContext(9L);
        for (var open = 0; open < openPatterns; open++) {
            session.learning().noteOpenPattern();
        }
        if (variety > 0) {
            session.patternVariety(variety);
        }
        fixture.coordinator.offerTable(table(), LearnedArtifacts.State.ACTIVE, "PASS", "all", 1, 7L);
        var sources = new ArrayList<String>();
        fixture.tap.attach(new DecisionTap() {
            @Override
            public void patternExploited(MobKey mob, akyrion.thepredict.domain.PatternExploit exploit) {
                sources.add(exploit.source());
            }
        }, () -> {
        });
        fixture.runUntilRequest(1);
        var tick = fixture.clock.currentTick();
        session.noteCastQuota((int) tick, false);
        session.recordInstall(new akyrion.thepredict.domain.InstallResult.Installed(3), 1L);
        session.facts(facts(session));
        fixture.client.futures.getFirst().complete(new DecisionResult.Failed(FailureKind.RATE_LIMITED,
                Optional.of(Duration.ofSeconds(30)), "rate limited"));
        fixture.run(2);
        return new Run(fixture, sources, tick, "", "");
    }

    private static Asked ask(LearnedArtifacts.State state, boolean shadow) {
        var fixture = new ExecutionFixture();
        fixture.ready();
        fixture.open(1, ExecutionFixture.profile(new DecisionTiming(1, 1, 100, 0.5, 0.2, 0), 0));
        fixture.target(1, 1);
        var session = fixture.session(1);
        session.candidateScoring(CandidateScoring.LEARNED);
        fixture.coordinator.useLearning(learning(false, shadow ? "shadow" : "off", shadow ? "shadow" : "off",
                ExploreSettings.DEFAULT));
        fixture.coordinator.offerTable(shadow ? voiTable() : table(), state, "PASS", "all", 1, 7L);
        var heard = new String[] {"", ""};
        var first = new String[] {""};
        var bodies = new ArrayList<String>();
        fixture.tap.attach(new DecisionTap() {
            @Override
            public void noteAnnotation(String voi, String jevHint) {
                if (first[0].isEmpty() && heard[0].isEmpty() && heard[1].isEmpty()) {
                    first[0] = voi;
                }
                heard[0] = voi;
                heard[1] = jevHint;
            }

            @Override
            public void requestBody(EncounterId encounter, long sequence, String body) {
                bodies.add(body);
            }
        }, () -> {
        });
        fixture.runUntilRequest(1);
        if (shadow) {
            fixture.answer(0, ExecutionFixture.nowAndContingency(ExecutionFixture.prefer(PRESS, 0.7, 0.9),
                    ExecutionFixture.prefer(PRESS, 0.7, 0.9)));
            fixture.runUntilRequest(2);
        }
        return new Asked(fixture, heard[0], heard[1], bodies, first[0]);
    }

    private static ValueTable voiTable() {
        var builder = new ValueTableBuilder();
        builder.add(1, LearnedLocalPick.tacticCode(KEEP.value()), 3, 7L, 1.0, 0.01, 60, 1.0, 80, 80, 0);
        builder.add(1, LearnedLocalPick.tacticCode(PRESS.value()), 3, 7L, 0.0, 0.01, 60, 1.0, 80, 80, 0);
        return builder.build();
    }

    private static ValueTable table() {
        var builder = new ValueTableBuilder();
        builder.add(1, LearnedLocalPick.tacticCode(KEEP.value()), 3, 7L, 1.0, 0.01, 40, 1.0);
        builder.add(1, LearnedLocalPick.tacticCode(PRESS.value()), 3, 7L, 0.0, 0.01, 40, 1.0);
        builder.add(1, LearnedLocalPick.tacticCode(KEEP.value()), 3, 9L, 0.0, 0.01, 40, 1.0);
        builder.add(1, LearnedLocalPick.tacticCode(PRESS.value()), 3, 9L, 1.0, 0.01, 40, 1.0);
        return builder.build();
    }

    private static long seed() {
        var probe = new MobSession(new MobKey(new UUID(2, 1), 0), EngineFixtures.profile("boss"));
        for (var drawn = 1L; drawn < 5000L; drawn++) {
            probe.reseed(drawn);
            if (ExplorePolicy.choose(probe.seed(), 4L, 0, 2, 0.10, 0, 0, 0.2, false, BehaviorPolicy.JEV_ARGMAX)
                    .inStratum()) {
                return drawn;
            }
        }
        throw new AssertionError("seed");
    }

    private static MobSession first(Encounter encounter) {
        return encounter.memberSessions().iterator().next();
    }

    private static Encounter encounter(boolean player) {
        var encounter = new Encounter(new EncounterId(28), new UUID(0, 1), 0);
        var session = new MobSession(new MobKey(new UUID(2, 1), 0), EngineFixtures.profile("boss"));
        encounter.addMember(session);
        var alias = player ? new ParticipantAlias(ParticipantKind.PLAYER, 1)
                : new ParticipantAlias(ParticipantKind.ENTITY, 1);
        var facts = player
                ? new ParticipantFacts(alias, HealthBand.HEALTHY, ArmorBand.LIGHT, Set.of(), 1)
                : new ParticipantFacts(alias, HealthBand.HEALTHY, ArmorBand.LIGHT, Set.of(), 1, Optional.of("zombie"));
        encounter.lastFacts(new CombatFacts(encounter.id(), 4L, 0L, List.of(), List.of(facts), List.of(), 1));
        return encounter;
    }

    private static EncounterQuestions.Plan plan(MobSession session) {
        var question = new akyrion.thepredict.domain.QuestionSpec(akyrion.thepredict.domain.QuestionKey.of(0),
                session.key(), akyrion.thepredict.domain.Situation.NOW, List.of(PRESS, KEEP), Optional.of(PRESS));
        return new EncounterQuestions.Plan(List.of(question), java.util.Map.of(session.key(), session.profile()),
                List.of());
    }

    private static LearningSettings learning(boolean duels, String voi, String hint, ExploreSettings exploration) {
        var defaults = LearningSettings.DEFAULT;
        return new LearningSettings(defaults.allowUnverified(), defaults.collect(), defaults.scope(),
                defaults.baseScoring(), defaults.trainingSources(), defaults.contextVersion(),
                defaults.maxContextKeysPerProfile(), defaults.maxOpenWindowsPerSession(), defaults.creditShare(),
                defaults.groupCreditShare(), defaults.creditDecay(), defaults.minWindowsForValue(),
                defaults.halfLifeDays(), defaults.weeksKept(), defaults.maxEntries(), defaults.minValueSpreadMillis(),
                defaults.buildChunkRows(), duels, exploration, defaults.calibrationMinDecisions(),
                defaults.priorStrength(), defaults.driftReferenceWindows(), voi, defaults.voiMinWindows(),
                defaults.voiMinAgreement(), defaults.voiMaxExpectedGain(), defaults.voiMaxConsecutiveSkips(), hint);
    }

    private static Tactic tactic(TacticId id, int priority) {
        return new Tactic(id, Movement.APPROACH, Optional.empty(), Optional.empty(), priority);
    }

    private static MobFacts facts(MobSession session) {
        var relation = new akyrion.thepredict.domain.TargetRelation(
                new ParticipantAlias(ParticipantKind.PLAYER, 1), akyrion.thepredict.domain.DistanceBand.RANGED,
                akyrion.thepredict.domain.Trend.STEADY, true, true, true, false, false);
        return new MobFacts(session.key(), HealthBand.HEALTHY, Optional.of(relation), Set.of(), Set.of(PRESS, KEEP),
                Set.of(), Set.of());
    }

    private record Run(ExecutionFixture fixture, List<String> sources, long decisionTick, String voi, String hint) {
    }

    private record Asked(ExecutionFixture fixture, String voi, String hint, List<String> bodies, String firstVoi) {
    }
}
